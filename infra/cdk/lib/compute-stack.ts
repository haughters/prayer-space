import * as fs from 'fs';
import * as path from 'path';
import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as events from 'aws-cdk-lib/aws-events';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as iam from 'aws-cdk-lib/aws-iam';


import { Construct } from 'constructs';

export interface ComputeStackProps extends cdk.StackProps {
  deployEnv: string;
  prayersTable: dynamodb.ITable;
  prayerUpdatesTable: dynamodb.ITable;
  groupsTable: dynamodb.ITable;
  groupMembersTable: dynamodb.ITable;
  devicesTable: dynamodb.ITable;
  adminsTable: dynamodb.ITable;
  intercessorAccountsTable: dynamodb.ITable;
  eventBus: events.IEventBus;
  notificationQueue: sqs.IQueue;
  bounceQueue: sqs.IQueue;
}

export class ComputeStack extends cdk.Stack {
  public readonly identityServiceAlias: lambda.Alias;
  public readonly prayerServiceAlias: lambda.Alias;
  public readonly groupServiceAlias: lambda.Alias;
  public readonly adminServiceAlias: lambda.Alias;
  public readonly notificationServiceAlias: lambda.Alias;

  public readonly identityFunctionUrl: lambda.FunctionUrl;
  public readonly prayerFunctionUrl: lambda.FunctionUrl;
  public readonly groupFunctionUrl: lambda.FunctionUrl;
  public readonly adminFunctionUrl: lambda.FunctionUrl;

  constructor(scope: Construct, id: string, props: ComputeStackProps) {
    super(scope, id, props);

    const createLambda = (name: string, environment: { [key: string]: string } = {}, hasUrl = false) => {
      const zipPath = path.resolve(__dirname, `../../services/${name}/target/${name}-lambda.zip`);
      const code = fs.existsSync(zipPath)
        ? lambda.Code.fromAsset(zipPath)
        : lambda.Code.fromAsset(path.join(__dirname, 'placeholder'));

      const fn = new lambda.Function(this, `${name}Lambda`, {
        functionName: `${props.deployEnv}-${name}`,
        runtime: lambda.Runtime.PROVIDED_AL2023,
        handler: 'bootstrap',
        code,
        memorySize: 512,
        timeout: cdk.Duration.seconds(30),
        environment: {
          SPRING_PROFILES_ACTIVE: 'default',
          GIT_COMMIT_SHA: this.node.tryGetContext('gitSha') || 'local',
          APP_TABLE_PREFIX: `${props.deployEnv}-`,
          ...environment,
        },
        currentVersionOptions: {
          removalPolicy: cdk.RemovalPolicy.RETAIN,
        },
      });

      const version = fn.currentVersion;

      const alias = new lambda.Alias(this, `${name}LiveAlias`, {
        aliasName: 'live',
        version,
      });

      let functionUrl: lambda.FunctionUrl | undefined = undefined;
      if (hasUrl) {
        functionUrl = alias.addFunctionUrl({
          authType: lambda.FunctionUrlAuthType.NONE,
          cors: {
            allowedOrigins: ['*'],
            allowedMethods: [lambda.HttpMethod.ALL],
            allowedHeaders: ['*'],
            allowCredentials: true,
          },
        });

        new cdk.CfnOutput(this, `${name}FunctionUrlOut`, {
          value: functionUrl.url,
        });
      }

      return { alias, functionUrl, fn };
    };

    // 1. identity-service
    const identitySvc = createLambda('identity-service', {}, true);
    this.identityServiceAlias = identitySvc.alias;
    this.identityFunctionUrl = identitySvc.functionUrl!;
    props.devicesTable.grantReadWriteData(this.identityServiceAlias);
    props.intercessorAccountsTable.grantReadWriteData(this.identityServiceAlias);
    props.groupsTable.grantReadData(this.identityServiceAlias);
    props.groupMembersTable.grantReadData(this.identityServiceAlias);

    // 2. group-service
    const groupSvc = createLambda('group-service', {}, true);
    this.groupServiceAlias = groupSvc.alias;
    this.groupFunctionUrl = groupSvc.functionUrl!;
    props.groupsTable.grantReadWriteData(this.groupServiceAlias);
    props.groupMembersTable.grantReadWriteData(this.groupServiceAlias);
    props.eventBus.grantPutEventsTo(this.groupServiceAlias);

    // 3. prayer-service
    const prayerSvc = createLambda('prayer-service', {
      AWS_EVENTBRIDGE_BUS_NAME: props.eventBus.eventBusName,
    }, true);
    this.prayerServiceAlias = prayerSvc.alias;
    this.prayerFunctionUrl = prayerSvc.functionUrl!;
    props.prayersTable.grantReadWriteData(this.prayerServiceAlias);
    props.prayerUpdatesTable.grantReadWriteData(this.prayerServiceAlias);
    props.groupsTable.grantReadData(this.prayerServiceAlias);
    props.groupMembersTable.grantReadData(this.prayerServiceAlias);
    props.eventBus.grantPutEventsTo(this.prayerServiceAlias);

    // 4. admin-service
    const adminSvc = createLambda('admin-service', {}, true);
    this.adminServiceAlias = adminSvc.alias;
    this.adminFunctionUrl = adminSvc.functionUrl!;
    props.adminsTable.grantReadWriteData(this.adminServiceAlias);
    props.intercessorAccountsTable.grantReadWriteData(this.adminServiceAlias);
    props.groupsTable.grantReadWriteData(this.adminServiceAlias);
    props.groupMembersTable.grantReadWriteData(this.adminServiceAlias);

    // 5. notification-service
    const notificationSvc = createLambda('notification-service', {
      AWS_SQS_QUEUE_URL: props.notificationQueue.queueUrl,
      AWS_SQS_BOUNCE_QUEUE_URL: props.bounceQueue.queueUrl,
    });
    this.notificationServiceAlias = notificationSvc.alias;
    props.groupMembersTable.grantReadData(this.notificationServiceAlias);
    props.notificationQueue.grantConsumeMessages(this.notificationServiceAlias);
    props.bounceQueue.grantConsumeMessages(this.notificationServiceAlias);
    this.notificationServiceAlias.role?.addToPrincipalPolicy(new iam.PolicyStatement({
      actions: ['ses:SendEmail', 'ses:SendRawEmail'],
      resources: ['*'],
    }));

    // Link inter-service endpoints to avoid circular references during instantiation
    identitySvc.fn.addEnvironment('SERVICES_GROUP_SERVICE_URL', groupSvc.functionUrl!.url);

    prayerSvc.fn.addEnvironment('SERVICES_GROUP_SERVICE_URL', groupSvc.functionUrl!.url);

    adminSvc.fn.addEnvironment('SERVICES_GROUP_SERVICE_URL', groupSvc.functionUrl!.url);
    adminSvc.fn.addEnvironment('SERVICES_PRAYER_SERVICE_URL', prayerSvc.functionUrl!.url);

    notificationSvc.fn.addEnvironment('SERVICES_GROUP_SERVICE_URL', groupSvc.functionUrl!.url);
    notificationSvc.fn.addEnvironment('SERVICES_PRAYER_SERVICE_URL', prayerSvc.functionUrl!.url);

    // Pass Queue Names to Notification Listener SpEL
    notificationSvc.fn.addEnvironment('AWS_SQS_NOTIFICATION_QUEUE', props.notificationQueue.queueName);
    notificationSvc.fn.addEnvironment('AWS_SQS_BOUNCE_QUEUE', props.bounceQueue.queueName);

    // Grant GitHub Actions Role permission to manage PR Lambdas
    const githubActionsRole = iam.Role.fromRoleName(this, 'GitHubActionsRole', 'GitHubActionsWorkflowDeployRole');
    
    const lambdaDeployPolicy = new iam.Policy(this, 'GitHubActionsLambdaDeployPolicy', {
      policyName: 'GitHubActionsLambdaDeployPolicy',
      statements: [
        new iam.PolicyStatement({
          actions: [
            'lambda:GetFunction',
            'lambda:CreateFunction',
            'lambda:UpdateFunctionCode',
            'lambda:UpdateFunctionConfiguration',
            'lambda:DeleteFunction',
            'lambda:AddPermission',
            'lambda:CreateFunctionUrlConfig',
            'lambda:GetFunctionUrlConfig',
            'lambda:DeleteFunctionUrlConfig'
          ],
          resources: [
            `arn:aws:lambda:${this.region}:${this.account}:function:pr-*`,
            `arn:aws:lambda:${this.region}:${this.account}:function:test-*`
          ]
        }),
        new iam.PolicyStatement({
          actions: ['iam:PassRole'],
          resources: [`arn:aws:iam::${this.account}:role/*`],
          conditions: {
            StringEquals: {
              'iam:PassedToService': 'lambda.amazonaws.com'
            }
          }
        })
      ]
    });
    lambdaDeployPolicy.attachToRole(githubActionsRole);
  }
}


import * as cdk from 'aws-cdk-lib';
import * as events from 'aws-cdk-lib/aws-events';
import * as targets from 'aws-cdk-lib/aws-events-targets';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import { Construct } from 'constructs';

export interface MessagingStackProps extends cdk.StackProps {
  deployEnv: string;
}

export class MessagingStack extends cdk.Stack {
  public readonly eventBus: events.EventBus;
  public readonly sesBouncesTopic: sns.Topic;
  public readonly notificationQueue: sqs.Queue;
  public readonly bounceQueue: sqs.Queue;

  constructor(scope: Construct, id: string, props: MessagingStackProps) {
    super(scope, id, props);

    // 1. Central Event Bus
    this.eventBus = new events.EventBus(this, 'PrayerLinkEventBus', {
      eventBusName: `${props.deployEnv}-prayer-link-bus`,
    });

    // 2. Dead Letter Queue for Notification Service
    const notificationDlq = new sqs.Queue(this, 'NotificationDlq', {
      queueName: `${props.deployEnv}-notification-dlq`,
      retentionPeriod: cdk.Duration.days(14),
    });

    // 3. Main Queue for Notification Service
    this.notificationQueue = new sqs.Queue(this, 'NotificationQueue', {
      queueName: `${props.deployEnv}-notification-queue`,
      visibilityTimeout: cdk.Duration.seconds(30),
      deadLetterQueue: {
        queue: notificationDlq,
        maxReceiveCount: 3, // Retry 3 times before sending to DLQ
      },
    });

    // 4. EventBridge Rule: Route Prayer events to Notification Queue
    const notificationRule = new events.Rule(this, 'NotificationRule', {
      eventBus: this.eventBus,
      ruleName: `${props.deployEnv}-prayer-events-to-notification-queue`,
      description: 'Routes prayer created/updated events to the notification service queue',
      eventPattern: {
        source: ['com.prayerlink.prayer-service'],
        detailType: ['PrayerCreated', 'PrayerUpdated'],
      },
    });

    notificationRule.addTarget(new targets.SqsQueue(this.notificationQueue, {
      deadLetterQueue: notificationDlq, // DLQ for EventBridge to SQS delivery failures
    }));

    // 5. SES Bounces SNS Topic
    this.sesBouncesTopic = new sns.Topic(this, 'SesBouncesTopic', {
      topicName: `${props.deployEnv}-prayer-link-ses-bounces`,
    });

    // 6. Bounce SQS Queue
    this.bounceQueue = new sqs.Queue(this, 'BounceQueue', {
      queueName: `${props.deployEnv}-bounce-queue`,
      visibilityTimeout: cdk.Duration.seconds(30),
    });

    // 7. Subscribe Bounce Queue to Bounces SNS Topic
    this.sesBouncesTopic.addSubscription(new subscriptions.SqsSubscription(this.bounceQueue));

    // Outputs
    new cdk.CfnOutput(this, 'EventBusName', {
      value: this.eventBus.eventBusName,
      exportName: `${props.deployEnv}-PrayerLinkEventBusName`,
    });

    new cdk.CfnOutput(this, 'NotificationQueueUrl', {
      value: this.notificationQueue.queueUrl,
      exportName: `${props.deployEnv}-PrayerLinkNotificationQueueUrl`,
    });

    new cdk.CfnOutput(this, 'SesBouncesTopicArn', {
      value: this.sesBouncesTopic.topicArn,
      exportName: `${props.deployEnv}-PrayerLinkSesBouncesTopicArn`,
    });

    new cdk.CfnOutput(this, 'BounceQueueUrl', {
      value: this.bounceQueue.queueUrl,
      exportName: `${props.deployEnv}-PrayerLinkBounceQueueUrl`,
    });
  }
}

import * as cdk from 'aws-cdk-lib';
import * as ses from 'aws-cdk-lib/aws-ses';
import * as sns from 'aws-cdk-lib/aws-sns';
import { Construct } from 'constructs';

export interface SesStackProps extends cdk.StackProps {
  deployEnv: string;
  sesBouncesTopic: sns.ITopic;
}

export class SesStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: SesStackProps) {
    super(scope, id, props);

    // 1. Create SES Configuration Set
    const configSet = new ses.ConfigurationSet(this, 'EmailConfigSet', {
      configurationSetName: `${props.deployEnv}-prayer-link-ses-config`,
    });

    // 2. Create Configuration Set Event Destination mapping to SNS topic
    new ses.CfnConfigurationSetEventDestination(this, 'SesBounceEventDestination', {
      configurationSetName: configSet.configurationSetName,
      eventDestination: {
        name: `${props.deployEnv}-sns-bounce-destination`,
        enabled: true,
        matchingEventTypes: ['BOUNCE'],
        snsDestination: {
          topicArn: props.sesBouncesTopic.topicArn,
        },
      },
    });

    // 3. Create verified email identity for sending emails
    const emailAddress = props.deployEnv === 'live' ? 'prayers@prayer-link.org' : 'prayers-test@prayer-link.org';
    new ses.EmailIdentity(this, 'EmailIdentity', {
      identity: ses.Identity.email(emailAddress),
      configurationSet: configSet,
    });

    console.log("SesStack initialized with bounce topic integration.");
  }
}

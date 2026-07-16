#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { DatabaseStack } from '../lib/database-stack';
import { MessagingStack } from '../lib/messaging-stack';
import { ComputeStack } from '../lib/compute-stack';
import { SesStack } from '../lib/ses-stack';
import { DnsStack } from '../lib/dns-stack';

const app = new cdk.App();

const deployEnv = app.node.tryGetContext('deployEnv') || 'test';
const prefix = deployEnv.charAt(0).toUpperCase() + deployEnv.slice(1);

// Environment configuration - customize as needed
const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION || 'eu-west-1',
};

// 1. Database Stack (DynamoDB Tables)
const databaseStack = new DatabaseStack(app, `${prefix}PrayerLinkDatabaseStack`, { env, deployEnv });

// 2. Messaging Stack (EventBridge, SQS)
const messagingStack = new MessagingStack(app, `${prefix}PrayerLinkMessagingStack`, { env, deployEnv });

// 3. Compute Stack (Lambda Functions)
const computeStack = new ComputeStack(app, `${prefix}PrayerLinkComputeStack`, {
  env,
  deployEnv,
  prayersTable: databaseStack.prayersTable,
  prayerUpdatesTable: databaseStack.prayerUpdatesTable,
  groupsTable: databaseStack.groupsTable,
  groupMembersTable: databaseStack.groupMembersTable,
  devicesTable: databaseStack.devicesTable,
  adminsTable: databaseStack.adminsTable,
  intercessorAccountsTable: databaseStack.intercessorAccountsTable,
  eventBus: messagingStack.eventBus,
  notificationQueue: messagingStack.notificationQueue,
  bounceQueue: messagingStack.bounceQueue,
});
computeStack.addDependency(databaseStack);
computeStack.addDependency(messagingStack);

// 4. SES Stack (Email Verification)
const sesStack = new SesStack(app, `${prefix}PrayerLinkSesStack`, {
  env,
  deployEnv,
  sesBouncesTopic: messagingStack.sesBouncesTopic,
});
sesStack.addDependency(messagingStack);

// 5. DNS Stack (CloudFront, Route53, Certs)
const dnsStack = new DnsStack(app, `${prefix}PrayerLinkDnsStack`, {
  env,
  deployEnv,
  computeStack: computeStack,
  sesStack: sesStack,
});
dnsStack.addDependency(computeStack);
dnsStack.addDependency(sesStack);

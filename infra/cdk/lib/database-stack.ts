import * as cdk from 'aws-cdk-lib';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { Construct } from 'constructs';

export interface DatabaseStackProps extends cdk.StackProps {
  deployEnv: string;
}

export class DatabaseStack extends cdk.Stack {
  public readonly prayersTable: dynamodb.Table;
  public readonly prayerUpdatesTable: dynamodb.Table;
  public readonly groupsTable: dynamodb.Table;
  public readonly groupMembersTable: dynamodb.Table;
  public readonly devicesTable: dynamodb.Table;
  public readonly adminsTable: dynamodb.Table;
  public readonly intercessorAccountsTable: dynamodb.Table;

  constructor(scope: Construct, id: string, props: DatabaseStackProps) {
    super(scope, id, props);

    const createTable = (id: string, name: string, partitionKeyName: string, sortKeyName?: string) => {
      const table = new dynamodb.Table(this, id, {
        tableName: `${props.deployEnv}-${name}`,
        partitionKey: { name: partitionKeyName, type: dynamodb.AttributeType.STRING },
        ...(sortKeyName ? { sortKey: { name: sortKeyName, type: dynamodb.AttributeType.STRING } } : {}),
        billingMode: dynamodb.BillingMode.PROVISIONED,
        readCapacity: 1,
        writeCapacity: 1,
        removalPolicy: props.deployEnv === 'live' ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
      });

      if (props.deployEnv === 'live') {
        table.autoScaleReadCapacity({ minCapacity: 1, maxCapacity: 10 }).scaleOnUtilization({ targetUtilizationPercent: 70 });
        table.autoScaleWriteCapacity({ minCapacity: 1, maxCapacity: 10 }).scaleOnUtilization({ targetUtilizationPercent: 70 });
      }

      return table;
    };

    const addGsi = (table: dynamodb.Table, indexName: string, partitionKeyName: string, sortKeyName?: string) => {
      table.addGlobalSecondaryIndex({
        indexName,
        partitionKey: { name: partitionKeyName, type: dynamodb.AttributeType.STRING },
        ...(sortKeyName ? { sortKey: { name: sortKeyName, type: dynamodb.AttributeType.STRING } } : {}),
        projectionType: dynamodb.ProjectionType.ALL,
        readCapacity: 1,
        writeCapacity: 1,
      });

      if (props.deployEnv === 'live') {
        table.autoScaleGlobalSecondaryIndexReadCapacity(indexName, { minCapacity: 1, maxCapacity: 10 }).scaleOnUtilization({ targetUtilizationPercent: 70 });
        table.autoScaleGlobalSecondaryIndexWriteCapacity(indexName, { minCapacity: 1, maxCapacity: 10 }).scaleOnUtilization({ targetUtilizationPercent: 70 });
      }
    };

    // 1. Prayers Table
    this.prayersTable = createTable('PrayersTable', 'Prayers', 'prayerId');
    addGsi(this.prayersTable, 'DeviceIdIndex', 'deviceId', 'createdAt');
    addGsi(this.prayersTable, 'GroupIdIndex', 'assignedGroupId', 'createdAt');

    // 2. PrayerUpdates Table
    this.prayerUpdatesTable = createTable('PrayerUpdatesTable', 'PrayerUpdates', 'prayerId', 'updatedAt');

    // 3. Groups Table
    this.groupsTable = createTable('GroupsTable', 'Groups', 'groupId');
    addGsi(this.groupsTable, 'PasscodeIndex', 'passcode');

    // 4. GroupMembers Table
    this.groupMembersTable = createTable('GroupMembersTable', 'GroupMembers', 'groupId', 'memberId');
    addGsi(this.groupMembersTable, 'EmailIndex', 'email');

    // 5. Devices Table
    this.devicesTable = createTable('DevicesTable', 'Devices', 'deviceId');

    // 6. Admins Table
    this.adminsTable = createTable('AdminsTable', 'Admins', 'adminId');
    addGsi(this.adminsTable, 'UsernameIndex', 'username');

    // 7. IntercessorAccounts Table
    this.intercessorAccountsTable = createTable('IntercessorAccountsTable', 'IntercessorAccounts', 'email');
  }
}

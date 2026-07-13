import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import { Construct } from 'constructs';
import { SesStack } from './ses-stack';
import { ComputeStack } from './compute-stack';

export interface DnsStackProps extends cdk.StackProps {
  deployEnv: string;
  computeStack: ComputeStack;
  sesStack: SesStack;
}

export class DnsStack extends cdk.Stack {
  public readonly frontendBucket: s3.Bucket;
  public readonly deployBucket: s3.Bucket;
  public readonly distribution: cloudfront.Distribution;

  constructor(scope: Construct, id: string, props: DnsStackProps) {
    super(scope, id, props);

    // 1. S3 Bucket for static frontend hosting
    this.frontendBucket = new s3.Bucket(this, 'FrontendBucket', {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      removalPolicy: props.deployEnv === 'live' ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: props.deployEnv !== 'live',
      versioned: true,
    });

    // S3 Bucket for PR Lambda zip deploy artifacts
    this.deployBucket = new s3.Bucket(this, 'DeployBucket', {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
      versioned: true,
    });

    // 2. CloudFront Origin Access Identity (OAI) for secure S3 access
    const originAccessIdentity = new cloudfront.OriginAccessIdentity(this, 'OAI');
    this.frontendBucket.grantRead(originAccessIdentity);

    // 3. Define Origins
    const s3Origin = origins.S3BucketOrigin.withOriginAccessIdentity(this.frontendBucket, { originAccessIdentity });

    const parseDomain = (url: string) => cdk.Fn.select(2, cdk.Fn.split('/', url));

    const identityOrigin = new origins.HttpOrigin(parseDomain(props.computeStack.identityFunctionUrl.url), {
      protocolPolicy: cloudfront.OriginProtocolPolicy.HTTPS_ONLY,
    });
    const prayerOrigin = new origins.HttpOrigin(parseDomain(props.computeStack.prayerFunctionUrl.url), {
      protocolPolicy: cloudfront.OriginProtocolPolicy.HTTPS_ONLY,
    });
    const groupOrigin = new origins.HttpOrigin(parseDomain(props.computeStack.groupFunctionUrl.url), {
      protocolPolicy: cloudfront.OriginProtocolPolicy.HTTPS_ONLY,
    });
    const adminOrigin = new origins.HttpOrigin(parseDomain(props.computeStack.adminFunctionUrl.url), {
      protocolPolicy: cloudfront.OriginProtocolPolicy.HTTPS_ONLY,
    });

    // 4. CloudFront Distribution
    this.distribution = new cloudfront.Distribution(this, 'CloudFrontDistribution', {
      defaultBehavior: {
        origin: s3Origin,
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
      },
      additionalBehaviors: {
        '/api/identity/*': {
          origin: identityOrigin,
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
          originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
        },
        '/api/prayers/*': {
          origin: prayerOrigin,
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
          originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
        },
        '/api/groups/*': {
          origin: groupOrigin,
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
          originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
        },
        '/api/admin/*': {
          origin: adminOrigin,
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
          originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
        },
        '/api/auth/*': {
          origin: adminOrigin,
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
          originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
        },
      },
      defaultRootObject: 'index.html',
      errorResponses: [
        {
          httpStatus: 404,
          responseHttpStatus: 200,
          responsePagePath: '/index.html',
          ttl: cdk.Duration.seconds(0),
        },
      ],
    });

    // Outputs
    new cdk.CfnOutput(this, 'FrontendBucketName', {
      value: this.frontendBucket.bucketName,
      exportName: `${props.deployEnv}-PrayerLinkFrontendBucketName`,
    });

    new cdk.CfnOutput(this, 'DeployBucketName', {
      value: this.deployBucket.bucketName,
      exportName: `${props.deployEnv}-PrayerLinkDeployBucketName`,
    });

    new cdk.CfnOutput(this, 'CloudFrontDistributionId', {
      value: this.distribution.distributionId,
      exportName: `${props.deployEnv}-PrayerLinkCloudFrontDistributionId`,
    });

    new cdk.CfnOutput(this, 'CloudFrontDomainName', {
      value: this.distribution.distributionDomainName,
      exportName: `${props.deployEnv}-PrayerLinkCloudFrontDomainName`,
    });
  }
}

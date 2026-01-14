import * as cdk from 'aws-cdk-lib';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as events from 'aws-cdk-lib/aws-events';
import * as targets from 'aws-cdk-lib/aws-events-targets';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as sns from 'aws-cdk-lib/aws-sns'
import * as subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import { Construct } from 'constructs';

export class PrecipitationAlarmCloudServiceStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // Lambda Configuration
    const precipitationAnalyzerLambda = new lambda.Function(this, "PrecipitationAnalyzerLambda", {
      runtime: lambda.Runtime.JAVA_21,
      handler: 'main.java.example.PrecipitationAnalyzerLambda::handleRequest',
      code: lambda.Code.fromAsset('build/distributions/precipitation-alarm-cloud-service.zip'),
    });

    precipitationAnalyzerLambda.applyRemovalPolicy(cdk.RemovalPolicy.DESTROY);

    // EventBridge Configuration
    const precipitationRule = new events.Rule(this, "PrecipitationRule", {
      schedule: events.Schedule.rate(cdk.Duration.minutes(15)),
    });

    precipitationRule.addTarget(new targets.LambdaFunction(precipitationAnalyzerLambda));
    precipitationRule.applyRemovalPolicy(cdk.RemovalPolicy.DESTROY);

    // DynamoDB Table Configuration
    const precipitationRecords = new dynamodb.Table(this, 'PrecipitationRecords', {
      partitionKey: {
        name: 'location',
        type: dynamodb.AttributeType.STRING,
      },
      encryption: dynamodb.TableEncryption.AWS_MANAGED,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    precipitationRecords.grantReadWriteData(precipitationAnalyzerLambda);

    // SNS Topic Configuration
    const precipitationEvaluationNotification = new sns.Topic(this, "precipitationEvaluationNotification");
    
    precipitationEvaluationNotification.addSubscription(new subscriptions.SmsSubscription("+5511942721988"));
    precipitationEvaluationNotification.grantPublish(precipitationAnalyzerLambda);
    precipitationAnalyzerLambda.addEnvironment('SNS_TOPIC_ARN', precipitationEvaluationNotification.topicArn);
  }
}

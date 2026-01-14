#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib/core';
import { PrecipitationAlarmCloudServiceStack } from '../lib/precipitation-alarm-cloud-service-stack';

const app = new cdk.App();
new PrecipitationAlarmCloudServiceStack(app, 'PrecipitationAlarmCloudServiceStack', {
  env: { account: '724772081727', region: 'us-west-2' },

});

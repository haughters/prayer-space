const { AwsClient } = require('aws4fetch');
const aws = new AwsClient({
  accessKeyId: process.env.AWS_ACCESS_KEY_ID,
  secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
  sessionToken: process.env.AWS_SESSION_TOKEN,
  region: 'eu-west-1',
  service: 'lambda'
});

async function run() {
  const url = 'https://uebeagfehht3so3mhwse6ihk340ouowq.lambda-url.eu-west-1.on.aws/api/prayers?deviceId=test-dev-123';
  const res = await aws.fetch(url);
  console.log('Status:', res.status);
  console.log('Body:', await res.text());
}
run();

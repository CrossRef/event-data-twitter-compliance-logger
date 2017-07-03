# Event Data Twitter Compliance Logger

Connects to the Gnip Compliance firehose and logs all content to S3. Stores IDs of tweet IDs and user IDs as JSON arrays of integers in a time-stamped file. Tweet IDs are split into chunks of 1,000,000 items, which is about 20 MB. About 12 of these are generated every hour, which works out as about five gigabytes of integers per day!

## Shutdown hooks

To test shutdown hooks, be sure to make Docker give the process enough time to tidy up gracefully. Use `lein trampoline run` rather than `lein run` so that signals can be handled gracefully.

  docker-compose start compliance
  docker-compose stop -t 60 compliance

## Config

The following config keys are required:

 - TWITTER_COMPLIANCE_S3_BUCKET_NAME
 - TWITTER_COMPLIANCE_S3_REGION_NAME
 - TWITTER_COMPLIANCE_S3_KEY
 - TWITTER_COMPLIANCE_S3_SECRET
 - TWITTER_GNIP_COMPLIANCE_URL
 - TWITTER_GNIP_PASSWORD
 - TWITTER_GNIP_USERNAME

 ## License

Copyright © Crossref

Distributed under the The MIT License (MIT).

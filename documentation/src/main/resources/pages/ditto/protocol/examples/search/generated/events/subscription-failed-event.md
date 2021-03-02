## SubscriptionFailed

```json
{
  "topic": "_/_/things/twin/search/failed",
  "headers": {
    "content-type": "application/json"
  },
  "path": "/",
  "value": {
    "subscriptionId": "24601",
    "error": {
      "status": 400,
      "error": "thing-search:subscription.protocol.error",
      "message": "Rule 3.9: While the Subscription is not cancelled, Subscription.request(long n) MUST signal onError with a java.lang.IllegalArgumentException if the argument is <= 0. The cause message SHOULD explain that non-positive request signals are illegal.",
      "description": "The intent of this rule is to prevent faulty implementations to proceed operation without any exceptions being raised. Requesting a negative or 0 number of elements, since requests are additive, most likely to be the result of an erroneous calculation on the behalf of the Subscriber."
    }
  }
}
```

# demo-notifier

Demo: receives demo-orders.created and tells the team about big orders

A Kotlin ([Micronaut](https://micronaut.io/)) app on the [swhurl platform](https://github.com/samclement/swhurl-platform), made from the [swhurl Kotlin template](https://github.com/samclement/swhurl-app-template-kotlin). Every push to `main` is checked, published to `ghcr.io/<owner>/demo-notifier` and deployed to staging; production changes through **Promote to production**. What the image must provide, the checks and the dependency updates: the [template's guide](https://github.com/samclement/swhurl-app-template-kotlin#readme). What the app needs from the platform: [`swhurl.yaml`](swhurl.yaml).

A background worker with no web address: `Worker.kt` runs `work()` every `WORK_INTERVAL_MS` (default a minute).

## Callers and calling other apps

`Platform.kt` holds what the platform gives the app ([calling another app](https://github.com/samclement/swhurl-platform/blob/main/docs/apps.md#calling-another-app)):

- **Calling another app.** Add `calls: [orders]` to `swhurl.yaml` before the app is created (afterwards: `make app-calls` on the platform, or the app page's **Calls** form). Then `platform.call("orders", "/path")` reaches the same environment's `orders` with this app's token for it; the callee's spans join the same trace.

## Notifications

`platform.notify("Order backlog", message = "412 orders waiting", priority = "high")` (in `Platform.kt`) sends a message to the notification channel of the team that owns this app ([notifications](https://github.com/samclement/swhurl-platform/blob/main/docs/apps.md#notifications)). Nothing is declared in `swhurl.yaml`: the platform gives every app the address and a token. The message arrives titled `<app>/<env>: Order backlog`. The call returns whether the platform accepted it and never throws; an app may send 30 an hour, and a team without a channel delivers nothing. Locally there is no notify service: `notify` returns `false` and sends nothing.

## Events

`Events.kt` publishes and receives events on the platform's NATS ([events](https://github.com/samclement/swhurl-platform/blob/main/docs/apps.md#events)). Declare them in `swhurl.yaml` before the app is created (afterwards: `make app-events` on the platform):

```yaml
events:
  publish: [created]               # other apps see demo-notifier.created
  subscribe: [orders.created]      # <app>.<event>
```

Then `events.publish("created", mapOf("id" to 42))` and `events.subscribe("orders.created") { event -> ... }`. An event is a CloudEvents envelope (`event.data` is what was published); a handler runs inside the publisher's trace, and if it throws the event comes back, five tries in all. `subscribe` keeps trying until it is receiving, since a new app can start before NATS knows it. `Worker.kt` logs every event it subscribes to: an example to replace. Locally there is no NATS: `publish` returns `null` and `subscribe` never calls back.

## Without the platform

Locally there are no platform keys or tokens: to call another app, set `SWHURL_SERVICE_<NAME>_URL` to a local stub.

## Local development

Needs a JDK 25 (Gradle downloads everything else):

```bash
./gradlew run                           # the agent stays off locally (the image loads it)
./gradlew check                         # compile and tests, as CI does
```

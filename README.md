# demo-notifier

> **A demo app of the swhurl platform.** With [demo-shop](https://github.com/samclement/demo-shop), [demo-orders](https://github.com/samclement/demo-orders) and [demo-shopper](https://github.com/samclement/demo-shopper) it shows how apps on the platform work together. This one shows:
>
> - **Receiving another app's event.** The platform grants it `demo-orders.created` (`make app-events`; nothing about it is in `swhurl.yaml`); `Worker.kt` handles each order demo-orders publishes, without knowing where demo-orders runs. An event missed while this app was down arrives when it is back.
> - **Telling the team.** An order of ten or more becomes a message on the notification channel of the team that owns this app (`platform.notify`). The steady demo traffic buys one to three, so it never notifies; buy twelve of something in demo-shop to see one.
> - **The second stack.** Kotlin on Micronaut, a worker with no web address; the other demo apps are TypeScript.

Demo: receives demo-orders.created and tells the team about big orders

A Kotlin ([Micronaut](https://micronaut.io/)) app on the [swhurl platform](https://github.com/samclement/swhurl-platform), made from the [swhurl Kotlin template](https://github.com/samclement/swhurl-app-template-kotlin). Every push to `main` is checked, published to `ghcr.io/<owner>/demo-notifier` and deployed to staging; production changes through **Promote to production**. What the image must provide, the checks and the dependency updates: the [template's guide](https://github.com/samclement/swhurl-app-template-kotlin#readme). What the app needs from the platform: [`swhurl.yaml`](swhurl.yaml).

A background worker with no web address: `Worker.kt` runs `work()` every `WORK_INTERVAL_MS` (default a minute).

## Callers and calling other apps

`Platform.kt` holds what the platform gives the app ([calling another app](https://github.com/samclement/swhurl-platform/blob/main/docs/apps.md#calling-another-app)):

- **Secrets.** List each secret variable the app reads under `secrets:` in `swhurl.yaml` and read it with `platform.secret("API_TOKEN")`. Its value is set on the platform, never here: `make app-secret APP=<app> ENV=<env> NAME=API_TOKEN` in the platform checkout. A name pushed before its value deploys all the same; `secret()` then fails with a message naming that command, and the app page shows it as value missing.
- **Calling another app.** A call is granted on the platform, not in this repository: tick `orders` under **Connections** when creating the app, or later use the app page's **Connect to an app** or `make app-calls`. Then `platform.call("orders", "/path")` reaches the same environment's `orders` with this app's token for it; the callee's spans join the same trace.

## Notifications

`platform.notify("Order backlog", message = "412 orders waiting", priority = "high")` (in `Platform.kt`) sends a message to the notification channel of the team that owns this app ([notifications](https://github.com/samclement/swhurl-platform/blob/main/docs/apps.md#notifications)). Nothing is declared in `swhurl.yaml`: the platform gives every app the address and a token. The message arrives titled `<app>/<env>: Order backlog`. The call returns whether the platform accepted it and never throws; an app may send 30 an hour, and a team without a channel delivers nothing. Locally there is no notify service: `notify` returns `false` and sends nothing.

## Feature flags

Declare a flag in `swhurl.yaml` with a type, a default and, where it differs, a value for `staging` or `prod` ([feature flags](https://github.com/samclement/swhurl-platform/blob/main/docs/apps.md#feature-flags)):

```yaml
flags:
  new-checkout: {type: boolean, description: Use the new checkout flow, default: false, staging: true}
```

Before every compile, `./gradlew flags` (`gradle/flags.gradle.kts`) checks the declarations and generates the `Flags` object under `build/generated/flags`: `if (Flags.newCheckout)`. On the platform an override set from the console wins: the app watches its own overrides over NATS (`FlagWatcher.kt`) and the next read returns the new value, without a restart. Otherwise a flag is the value declared for `SWHURL_ENV`, else its default, which is also what local runs get. A restart keeps the override; `/healthz` reports not ready until the stored overrides arrive (at most 5 s). Types: `boolean`, `integer` (`Int`), `number` (`Double`; both with optional `min` and `max`) and `string` (with an optional `enum`). To remove a flag, delete it from `swhurl.yaml`: the compiler then names each place that still reads it.

## Events

`Events.kt` publishes and receives events on the platform's NATS ([events](https://github.com/samclement/swhurl-platform/blob/main/docs/apps.md#events)). What it publishes is an offer: declare it in `swhurl.yaml` before the app is created (afterwards: `make app-events` on the platform):

```yaml
events:
  publish: [created]               # other apps see demo-notifier.created
```

What it receives is a grant, set on the platform: tick `orders.created` under **Connections** when creating the app, or later use the app page's **Connect to an app** or `make app-events ARGS="--add-subscribe orders.created"`.

Then `events.publish("created", mapOf("id" to 42))` and `events.subscribe("orders.created") { event -> ... }`. An event is a CloudEvents envelope (`event.data` is what was published); a handler runs inside the publisher's trace, and if it throws the event comes back, five tries in all. `subscribe` keeps trying until it is receiving, since a new app can start before NATS knows it. `Worker.kt` logs every event it subscribes to: an example to replace. Locally there is no NATS: `publish` returns `null` and `subscribe` never calls back.

## Without the platform

Locally there are no platform keys or tokens: to call another app, set `SWHURL_SERVICE_<NAME>_URL` to a local stub.

## Local development

Needs a JDK 25 (Gradle downloads everything else):

```bash
./gradlew run                           # the agent stays off locally (the image loads it)
./gradlew check                         # compile and tests, as CI does
```

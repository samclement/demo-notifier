# demo-notifier

> **A demo app of the swhurl platform.** With [demo-shop](https://github.com/samclement/demo-shop), [demo-orders](https://github.com/samclement/demo-orders) and [demo-shopper](https://github.com/samclement/demo-shopper) it shows how apps on the platform work together. This one shows:
>
> - **Receiving another app's event.** The platform grants it `demo-orders.created` (`make app-events`; nothing about it is in `swhurl.yaml`); `Worker.kt` handles each order demo-orders publishes, without knowing where demo-orders runs. An event missed while this app was down arrives when it is back.
> - **Telling the team.** An order of ten or more becomes a message on the notification channel of the team that owns this app (`platform.notify`). The steady demo traffic buys one to three, so it never notifies; buy twelve of something in demo-shop to see one.
> - **The second stack.** Kotlin on Micronaut, a worker with no web address; the other demo apps are TypeScript.

Demo: receives demo-orders.created and tells the team about big orders

A Kotlin ([Micronaut](https://micronaut.io/)) app on the [swhurl platform](https://github.com/samclement/swhurl-platform), made from the [swhurl Kotlin template](https://github.com/samclement/swhurl-app-template-kotlin). Every push to `main` is checked, published to `ghcr.io/<owner>/demo-notifier` and deployed to staging; production changes through **Promote to production**. What the image must provide, the checks and the dependency updates: the [template's guide](https://github.com/samclement/swhurl-app-template-kotlin#readme). What the app needs from the platform: [`swhurl.yaml`](swhurl.yaml). Its `description` is the line above: the platform reads it with every image and passes it to the app as `SWHURL_DESCRIPTION`.

A background worker with no web address: `Worker.kt` runs `work()` every `WORK_INTERVAL_MS` (default a minute).

## Callers and calling other apps

`Platform.kt` holds what the platform gives the app ([calling another app](https://github.com/samclement/swhurl-platform/blob/main/docs/apps.md#calling-another-app)):

- **Secrets.** List each secret variable the app reads under `secrets:` in `swhurl.yaml` and read it with `platform.secret("API_TOKEN")`. Its value is set on the platform, never here: `make app-secret APP=<app> ENV=<env> NAME=API_TOKEN` in the platform checkout. A name pushed before its value deploys all the same; `secret()` then fails with a message naming that command, and the app page shows it as value missing.
- **Calling another app.** A call is granted on the platform, not in this repository: tick `orders` under **Connections** when creating the app, or later use the app page's **Connect to an app** or `make app-calls`. Then `platform.call("orders", "/path")` reaches the same environment's `orders` with this app's token for it; the callee's spans join the same trace. Pass the request's caller to say which signed-in person the call is for: `platform.call("orders", "/path", onBehalfOf = caller)`, and `orders` sees `Caller("app", "<this app>", user = "<email>")`. A browser should call only this app's own routes; this app then calls the APIs it needs on the person's behalf.

## Reaching outside the cluster

A request to a host outside the cluster goes through the platform's egress proxy, which knows this app by the port it uses ([reaching outside the cluster](https://github.com/samclement/swhurl-platform/blob/main/docs/apps.md#reaching-outside-the-cluster)). Which hosts the app may reach is a grant on the platform, per environment, not in this repository: the app page's **Reach a host outside**, or `make app-egress APP=demo-notifier ENV=staging ARGS="--add api.example.com"` (port 443 unless named; `*.example.com` is every name under it). Production is granted on its own, after promotion: a promotion never widens it.

- **Today nothing is refused.** The proxy logs every request with whether a grant covers it: in ClickStack, service `egress`, a `granted=-` line with this app's name is a host to grant before the platform starts refusing ungranted hosts and connections that bypass the proxy ([plan](https://github.com/samclement/swhurl-platform/blob/main/docs/plan.md#stage-5-egress-grants)).
- **Other apps, events, notifications and telemetry** stay inside the cluster and never use the proxy (`NO_PROXY`).
- **Clients that use the proxy with no code:** `java.net.http.HttpClient` with its default proxy selector (what `platform.call` uses) and any client that reads the JVM's `https.proxyHost` and `http.nonProxyHosts` properties, which the platform sets in `JAVA_TOOL_OPTIONS`.
- **Micronaut's HTTP client ignores them**: it uses no proxy unless configured. If you add `micronaut-http-client`, give it the JVM's selector:

  ```kotlin
  @Singleton
  class UseTheProxy : BeanCreatedEventListener<HttpClientConfiguration> {
      override fun onCreated(event: BeanCreatedEvent<HttpClientConfiguration>): HttpClientConfiguration =
          event.bean.apply { setProxySelector(java.net.ProxySelector.getDefault()) }
  }
  ```
- **An SDK with its own HTTP client** (a payment or cloud provider's): check that it follows the JVM's proxy properties, or give it the proxy from `HTTPS_PROXY` in its own settings.
- **Locally** there is no proxy: the variables are unset and requests go direct.

## Notifications

`platform.notify("Order backlog", message = "412 orders waiting", priority = "high")` (in `Platform.kt`) sends a message to the notification channel of the team that owns this app ([notifications](https://github.com/samclement/swhurl-platform/blob/main/docs/apps.md#notifications)). Nothing is declared in `swhurl.yaml`: the platform gives every app the address and a token. The message arrives titled `<app>/<env>: Order backlog`. The call returns whether the platform accepted it and never throws; an app may send 30 an hour, and a team without a channel delivers nothing. Locally there is no notify service: `notify` returns `false` and sends nothing.

## Feature flags

Declare a flag in `swhurl.yaml` with a type, a default and, where it differs, a value for `staging` or `prod` ([feature flags](https://github.com/samclement/swhurl-platform/blob/main/docs/apps.md#feature-flags)):

```yaml
flags:
  new-checkout: {type: boolean, description: Use the new checkout flow, default: false, staging: true}
```

Before every compile, `./gradlew flags` (`gradle/flags.gradle.kts`) checks the declarations and generates the `Flags` object under `build/generated/flags`: `if (Flags.newCheckout)`. On the platform an override set from the console wins: the app watches its own overrides over NATS (`FlagWatcher.kt`) and the next read returns the new value, without a restart. Otherwise a flag is the value declared for `SWHURL_ENV`, else its default, which is also what local runs get. A restart keeps the override; `/healthz` reports not ready until the stored overrides arrive (at most 5 s). Types: `boolean`, `integer` (`Int`), `number` (`Double`; both with optional `min` and `max`) and `string` (with an optional `enum`). To remove a flag, delete it from `swhurl.yaml`: the compiler then names each place that still reads it. `shout`, read in `Worker.kt`, is an example to replace.

## Events

`Events.kt` publishes and receives events on the platform's NATS ([events](https://github.com/samclement/swhurl-platform/blob/main/docs/apps.md#events)). What it publishes is an offer: declare it in `swhurl.yaml` before the app is created (afterwards: `make app-events` on the platform), or, as this app does, in `src/main/resources/asyncapi.yaml`:

```yaml
events:
  publish: [created]               # other apps see demo-notifier.created
```

What it receives is a grant, set on the platform: tick `orders.created` under **Connections** when creating the app, or later use the app page's **Connect to an app** or `make app-events ARGS="--add-subscribe orders.created"`.

Then `events.publish("created", mapOf("id" to 42))` and `events.subscribe("orders.created") { event -> ... }`. An event is a CloudEvents envelope (`event.data` is what was published); a handler runs inside the publisher's trace, and if it throws the event comes back, five tries in all. `subscribe` keeps trying until it is receiving, since a new app can start before NATS knows it. `Worker.kt` publishes `worked` after every run and logs every event it subscribes to: an example to replace. Locally there is no NATS: `publish` returns `null` and `subscribe` never calls back.

### The events this app offers: `asyncapi.yaml`

[`src/main/resources/asyncapi.yaml`](src/main/resources/asyncapi.yaml) describes each event demo-notifier publishes ([AsyncAPI 3](https://www.asyncapi.com/docs)): its name, what it means, the schema of its payload and an example. It is the declaration. The platform reads it to show other apps what they can subscribe to, and `events.publish()` throws `EventInvalid` for an event that is not in it or a payload that does not fit, locally as well as on the platform.

To add an event, add to `asyncapi.yaml` a channel (its `address` is the event's name), a message with a `payload` schema and an example, and a `send` operation; then call `events.publish("<name>", data)`. `AsyncApiTest` checks that every event has a description, a schema and an example that fits it. Nothing else is declared: after the push, the platform reads the file for the new image and other apps can subscribe ([what an app offers](https://github.com/samclement/swhurl-platform/blob/main/docs/apps.md#what-an-app-offers)).

## Without the platform

Locally there are no platform keys or tokens: to call another app, set `SWHURL_SERVICE_<NAME>_URL` to a local stub.

## Local development

Needs a JDK 25 (Gradle downloads everything else):

```bash
./gradlew run                           # the agent stays off locally (the image loads it)
./gradlew check                         # compile and tests, as CI does
```

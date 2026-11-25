# Spec: Allow specifying the service URL on `@CXFClient`

Issue: [#2460](https://github.com/quarkiverse/quarkus-cxf/issues/2460) ·
Follow-up of [#2457](https://github.com/quarkiverse/quarkus-cxf/issues/2457) ·
Related: [#2473](https://github.com/quarkiverse/quarkus-cxf/issues/2473)

The issue is small to medium sized and it already records most of the decisions.
This spec therefore mostly restates those decisions as requirements
and adds the questions that came up when they were checked against the current code.

## Background and motivation

Since #2457 (Quarkus CXF 4.0.0), every client must have `quarkus.cxf.client."client-name".client-endpoint-url` set,
otherwise it fails when it is created.
That is the right default for services whose URL differs per environment.
It is verbose where the URL is known at the injection point: tests, demos, and clients calling a fixed well-known URL.
The issue proposes to allow specifying the URL directly on `@io.quarkiverse.cxf.annotation.CXFClient`,
while keeping the configuration option as the way to override the URL per environment without recompiling.

## Use cases

* UC-1: Specify the URL of a named client at the injection point,
  e.g. `@CXFClient(value = "myCalculator", url = "https://example.org/calculator-ws/CalculatorService")`.
* UC-2: Override the URL given in the annotation per environment
  through `quarkus.cxf.client."client-name".client-endpoint-url`.
* UC-3: Specify the URL on a `@CXFClient` without a client name.
* UC-4: Use property expressions in the URL, e.g. `${calculator.baseUri}/calculator-ws/CalculatorService`.
* UC-5: Inject `CXFClientInfo` via `@CXFClient` and get the same effective URL as the client itself.
* UC-6: See the effective URL of such clients in Dev UI.

## Use case validation

* All use cases are in line with the project mission.
  They can be accomplished already by setting `client-endpoint-url` in the configuration,
  so the feature is a convenience and the configuration option stays the primary mechanism.
* UC-1: A single injection point cannot see the other injection points at run time,
  thus detecting two injection points with different URLs requires data collected at build time.
  `CxfClientProcessor.findClientInjectionPoints()` already scans all `@CXFClient` occurrences in the Jandex index.
* UC-3: A client without a name is associated with the only `quarkus.cxf.client."client-name"`
  having `service-interface` set to its service endpoint interface.
  Since #2457, there is no client without such a configuration:
  `CXFClientInfo` requires a non-null client name
  and uses it as the key of the Vert.x `HttpClient` pool (`HttpClientPool.getClient()`), in metric names,
  for looking up the logging configuration and in exception messages.
  The issue allows `url` on such a client also when no configuration is available,
  thus the client needs some effective name.
  See OQ-2.
* UC-4: SmallRye Config has no public API for expanding expressions in an arbitrary string (#2473).
  Quarkus Scheduler's `SchedulerUtils.resolvePropertyExpression()` is the precedent named in the issue.
  It uses other `Expression.Flag`s than `io.smallrye.config.ExpressionConfigSourceInterceptor`,
  so the syntax accepted in annotations differs slightly from the one accepted in `application.properties`.
  See OQ-6.
* UC-5: `CxfClientInfoProducer` finds the service endpoint interface through
  `quarkus.cxf.client."client-name".service-interface`.
  Hence injecting `CXFClientInfo` requires a client name having `service-interface` set, with or without `url`.
* UC-6: Dev UI lists one row per distinct `ClientInjectionPoint` (client name and service endpoint interface)
  and it computes the address without creating a `CXFClientInfo`,
  so that clients that cannot be created are listed too.
* The code example in the issue uses `clientEndpointUrl` as the attribute name,
  while the decision further down in the issue and #2473 say `url`.
  This spec follows the explicit decision.

## Scope

Goals:

* A new `url` attribute on `@CXFClient`
* Precedence of `client-endpoint-url` over the attribute
* Property expressions in the attribute value
* The same effective URL for the client, for `CXFClientInfo` and in Dev UI
* Documentation and tests

Non-goals:

* Any change of the behavior of injection points that do not use the new attribute,
  except for the wording of the exception messages about the missing URL
* Secret handler expressions (`${handler::value}`) in the attribute value
* Using a SmallRye Config API for expanding the expressions - tracked in #2473
* Injecting `CXFClientInfo` without a client name
* Accepting the `wsdl:port` address or `BindingProvider.ENDPOINT_ADDRESS_PROPERTY` instead of a URL - decided in #2457
* Other attributes on `@CXFClient`, such as the WSDL location
* Improving how Dev UI renders clients without an address

## Functional requirements

* R-1: `@CXFClient` has a new attribute `String url() default ""`.
  It is `@Nonbinding` like `value`.
  An empty value means that no URL is specified at the given injection point.
* R-2: If `quarkus.cxf.client."client-name".client-endpoint-url` is set for the client, it is the effective URL,
  regardless of the attribute.
* R-3: Otherwise, if the attribute is not empty, the attribute value with its property expressions expanded
  is the effective URL.
* R-4: Otherwise, the client fails when it is created.
  The message names the client and both ways of setting the URL.
* R-5: The attribute is allowed on a `@CXFClient` without a client name.
  Such a client is still associated with the only `quarkus.cxf.client."client-name"` having `service-interface`
  set to its service endpoint interface, if there is such one, and R-2 applies to that configuration.
  If there is no such configuration,
  the client is created with the default configuration and the URL from the attribute.
* R-6: If two injection points with the same effective client name (can be also implicit by type) 
  specify different URLs (empty is compared as "" value), the client fails when it is created.
  The raw unexpanded attribute values are compared.
* R-7: A `CXFClientInfo` injected via `@CXFClient` gets its endpoint address through the same mechanism (R-2 to R-6).
* R-8: Dev UI shows the effective URL computed through the same mechanism.
* R-9: The attribute value may contain property expressions,
  such as `${calculator.baseUri}/calculator-ws/CalculatorService`.
  The keys are looked up in the application configuration,
  so that nested expressions, profiles and `${key:default}` work.
  Secret handler expressions are not supported.
* R-10: If R-2 applies, the attribute value is not evaluated at all.
  An expression that cannot be resolved does not fail a client whose URL is set in the configuration.
* R-11: If R-3 applies and the expression cannot be resolved, the client fails when it is created.
  The message names the key that could not be resolved, the client and the raw attribute value.
* R-12: The attribute is documented in the JavaDoc of `@CXFClient`, in the user guide,
  in the description of `client-endpoint-url` in the configuration reference and in the release notes.

## Non-functional requirements

* NFR-1 Compatibility: The change is additive.
  Applications not using the attribute behave as before, except for the wording of the messages mentioned in R-4.
* NFR-2 Maintainability: The expression expansion is isolated in one place,
  so that it can be replaced by a SmallRye Config API once #2473 is resolved.
* NFR-3 Native: The feature works in native mode without additional configuration by the user.
* NFR-4 Dependencies: No new dependency is introduced.
  `io.smallrye.common:smallrye-common-expression` comes through `smallrye-config-core` already.
* NFR-5 Security: Exception messages contain only the raw unexpanded attribute values,
  because expanded values may contain credentials coming from the configuration.

## Acceptance criteria

* AC-1 (R-1, R-3): A client injected with `@CXFClient(value = "c", url = "<literal URL>")`
  and without `quarkus.cxf.client.c.client-endpoint-url` sends its requests to that URL.
* AC-2 (R-1): Existing injection points using `@CXFClient("c")` and `@CXFClient` compile and work unchanged.
  Field, constructor parameter and `Instance<T>` injection points are covered.
* AC-3 (R-2): With both `url` and `client-endpoint-url` set, the client uses the value of `client-endpoint-url`.
* AC-4 (R-4): With neither of the two set, creating the client fails with a message
  naming the client, `client-endpoint-url` and the `url` attribute.
* AC-5 (R-5): `@CXFClient(url = "...")` without a client name and without any matching `service-interface` works.
* AC-6 (R-5): `@CXFClient(url = "...")` without a client name and with a matching `service-interface`
  uses the settings of that configuration; its `client-endpoint-url` wins if it is set,
  otherwise the URL from the attribute is used.
* AC-7 (R-6): Two injection points with the same client name and different `url` values fail when the client is created
  and the message names the client and both raw values.
* AC-8 (R-6): Two injection points with the same client name and the same raw `url` value work.
  Two different raw values that expand to the same URL fail.
* AC-9 (R-7): `CXFClientInfo.getEndpointAddress()` of a `CXFClientInfo`
  injected via `@CXFClient(value = "c", url = "...")`
  returns the same URL as the client `c` uses, and it fails in the same situations as the client.
* AC-10 (R-8): Dev UI lists the effective URL of a client whose URL is specified only through the attribute,
  and the value of `client-endpoint-url` when both are set.
* AC-11 (R-9): `${key}`, `${key:default}`, an expression referring to a property that itself contains an expression,
  and an expression combined with literal text are expanded.
* AC-12 (R-10): A client having `client-endpoint-url` set and `url = "${not.defined}"` is created successfully.
* AC-13 (R-11): A client without `client-endpoint-url` and with `url = "${not.defined}"` fails when it is created
  with a message naming `not.defined`.
* AC-14 (R-12): The documentation pages render and the Java snippet showing the attribute comes from a test run by CI.

## Open questions

Each question comes with a proposed decision, so that the planning can proceed.

* OQ-2: What is the client name of a client injected without a name, for which there is no configuration
  with a matching `service-interface` (R-5)?
  Decision: The fully qualified class name of the service endpoint interface.
  The options under `quarkus.cxf.client."<sei>".*` would then apply to such a client.
* OQ-3: Does R-6 fail a client whose URL is set through `client-endpoint-url`?
  Decision: No.
  In line with R-10, the attribute values are consulted only if `client-endpoint-url` is not set.
  This also explains why the failure happens on client creation rather than at build time.
* OQ-4: How does R-6 apply to injection points without a client name?
  Decision: The injection points are grouped by the name of the configuration they are associated with,
  or by the name proposed in OQ-2 if there is none.
  Then `@CXFClient(url = "a")` and `@CXFClient(url = "b")` for the same service endpoint interface fail.
* OQ-5: What if the attribute value expands to an empty or blank string, e.g. `${key:}`?
  Decision: It is treated as not set, so R-4 applies.
* OQ-6: Which expression syntax flags are used?
  Decision: The ones of `io.smallrye.config.ExpressionConfigSourceInterceptor`,
  so that the attribute accepts the same syntax as `application.properties`
  and a later migration to a SmallRye Config API (#2473) does not change the behavior.
  A secret handler expression fails with a message saying that it is not supported.
* OQ-7: What does Dev UI show if the effective URL cannot be computed (R-6, R-11)?
  Decision: No address, as for a client without any URL today.
* OQ-8: Which release does the feature go to?
  Decision: 4.1.0, used for `@since` and for the release notes.
  The current development version is 4.0.1-SNAPSHOT.

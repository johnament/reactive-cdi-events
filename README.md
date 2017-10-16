# Project Reactor Meets CDI Events

Sometimes a good weekend in the mountains gets the creative juices flowing.

So what happens when you mix [Project Reactor](https://projectreactor.io/) with the CDI event programming model?  You get to `Flux` your way through events!

The built in event model in CDI only supports `void` methods or async invocations.  You can convert the resulting `CompletionStage` to a `Mono` using an approach like this

```java
CompletionStage<String> stringCompletionStage = seContainer.getBeanManager().getEvent().fireAsync("");
Mono<String> mono = Mono.fromCompletionStage(stringCompletionStage);
```

But that only allows you to ever look at one result.  I wanted to beable to get all of the results.  There is quite a bit of code required to make it work, but I can fully support an observer method pattern with injected dependencies dynamically.

To get started, inject a `ReactorEvent<S,R>` where `S` is the sender type, and `R` is the return type.

```java
    @Inject
    private ReactorEvent<String, String> event;

    public void fluxTheCapacitor() {
        event.fire("my incoming msg")
                .publishOn(Schedulers.single())
                .log("com.foo.bar", Level.SEVERE)
                .subscribe(System.out::println);
    }
```

This sends an event with payload `my incoming msg` and will print out any results it receives.  You can register an observer

```java
    public Flux<String> handle(@ObservesReactor String msg) {
        return Flux.just("bob");
    }
    public String another(@ObservesReactor String msg, FooBarBean fooBarBean) {
        return "ralph "+msg+fooBarBean.toString();
    }
```


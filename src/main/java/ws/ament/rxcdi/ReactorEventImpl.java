/*
 * Copyright 2017 Hammock and its contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ws.ament.rxcdi;

import reactor.core.publisher.Flux;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;

public class ReactorEventImpl<S,R> implements ReactorEvent<S, R> {
    private final Class<S> senderType;
    private final Class<R> returnType;
    private final Collection<Annotation> qualifiers;
    private final ReactorObserverRegistry registry;

    public ReactorEventImpl(Class<S> senderType, Class<R> returnType, Collection<Annotation> qualifiers, ReactorObserverRegistry registry) {
        this.senderType = senderType;
        this.returnType = returnType;
        this.qualifiers = qualifiers;
        this.registry = registry;
    }

    @Override
    public ReactorEvent<S, R> select(Annotation... qualifiers) {
        List<Annotation> collectedQualifiers = new ArrayList<>();
        collectedQualifiers.addAll(this.qualifiers);
        collectedQualifiers.addAll(asList(qualifiers));
        return new ReactorEventImpl<>(senderType, returnType, collectedQualifiers, this.registry);
    }

    @Override
    public Flux<R> fire(S s) {
        return registry.findBy(senderType, returnType, qualifiers).flux(s);
    }

    @Override
    public String toString() {
        return "ReactorEvent(" + "senderType=" + senderType +
                ", returnType=" + returnType +
                ", qualifiers=" + qualifiers +
                ')';
    }
}

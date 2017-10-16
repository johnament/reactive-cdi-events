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

import java.util.Collection;

import static java.util.stream.Collectors.toList;

public class ReactorMethods<T> {
    private Collection<PublisherHolder<T>> publisherHolders;

    public ReactorMethods(Collection<PublisherHolder<T>> publisherHolders) {
        this.publisherHolders = publisherHolders;
    }

    public Flux<T> flux(Object event) {
        return Flux.merge(publisherHolders.stream().map(ph -> ph.get(event)).collect(toList()));
    }
}

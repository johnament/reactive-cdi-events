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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.inject.spi.WithAnnotations;
import java.lang.reflect.ParameterizedType;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

public class ReactorExtension implements Extension {
    private final ReactorObserverRegistry REGISTRY = new ReactorObserverRegistry();
    private final Set<AnnotatedType<?>> discoveredTypes = new LinkedHashSet<>();
    private final Set<InjectionPoint> eventTypes = new LinkedHashSet<>();
    public void findObservers(@Observes @WithAnnotations(ObservesReactor.class)ProcessAnnotatedType<?> pat) {
        discoveredTypes.add(pat.getAnnotatedType());
    }
    public void locateInjections(@Observes ProcessInjectionPoint<?, ReactorEvent<?,?>> pip) {
        eventTypes.add(pip.getInjectionPoint());
    }
    public void addRegistryBean(@Observes AfterBeanDiscovery afterBeanDiscovery) {
        afterBeanDiscovery.addBean()
                .types(ReactorObserverRegistry.class)
                .produceWith((Function<Instance<Object>, Object>) f -> REGISTRY)
                .scope(ApplicationScoped.class);
        eventTypes.forEach(ip -> afterBeanDiscovery.addBean()
                .types(ip.getType())
                .scope(Dependent.class)
                .produceWith((Function<Instance<Object>, Object>) inst -> {
                    ParameterizedType type = (ParameterizedType) ip.getType();
                    Class<?> s = (Class<?>) type.getActualTypeArguments()[0];
                    Class<?> r = (Class<?>) type.getActualTypeArguments()[1];
                    return new ReactorEventImpl<>(s,r,ip.getQualifiers(),REGISTRY);
                }));
    }
    public void afterDeployment(@Observes AfterDeploymentValidation afterDeploymentValidation) {
        REGISTRY.process(discoveredTypes);
    }
}

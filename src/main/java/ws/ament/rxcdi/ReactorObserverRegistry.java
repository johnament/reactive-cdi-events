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

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Stereotype;
import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Qualifier;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

@Vetoed
@SuppressWarnings("unchecked")
public class ReactorObserverRegistry {
    private final List<ReactorMethod> methods = new ArrayList<>();

    <R> ReactorMethods<R> findBy(Class<?> eventPayload, Class<R> result, Collection<Annotation> annotationCollection) {
        Set<Annotation> eventQualifiers = findQualifiers(new LinkedHashSet<>(annotationCollection));
        List<PublisherHolder<R>> methods = new ArrayList<>();
        this.methods.stream()
                .filter(rm -> rm.matches(result, eventPayload, eventQualifiers))
                .forEach(methods::add);
        return new ReactorMethods<R>(methods);
    }

    public void process(Set<AnnotatedType<?>> discoveredTypes) {
        for(AnnotatedType<?> at : discoveredTypes) {
            for(AnnotatedMethod<?> am : at.getMethods()) {
                boolean hasObserver = am.getParameters().stream().anyMatch(ap -> ap.getAnnotation(ObservesReactor.class) != null);
                if(hasObserver) {
                    AnnotatedParameter<?> observerType = am.getParameters().stream().filter(ap -> ap.isAnnotationPresent(ObservesReactor.class)).findFirst().orElseThrow(() -> new RuntimeException("No observer found"));
                    Class<?> returnType = null;
                    Type genericReturnType = am.getJavaMember().getGenericReturnType();
                    boolean returnsPublisher = false;
                    if(genericReturnType instanceof ParameterizedType) {
                        ParameterizedType type = (ParameterizedType)genericReturnType;
                        if(Publisher.class.isAssignableFrom((Class<?>) type.getRawType())) {
                            returnsPublisher = true;
                            returnType = (Class<?>) type.getActualTypeArguments()[0];
                        } else {
                            throw new RuntimeException("Method "+am+" should either return a concrete type or an instance of "+Publisher.class.getName());
                        }
                    }
                    else {
                        returnType = (Class)genericReturnType;
                    }
                    Class<?> eventType = observerType.getJavaParameter().getType();
                    Set<Annotation> annotations = new LinkedHashSet<>(asList(observerType.getJavaParameter().getAnnotations()));
                    methods.add(new ReactorMethod(at,am,returnType,eventType,annotations,returnsPublisher));
                }
            }
        }
    }

    private static class ReactorMethod<T> implements PublisherHolder<T>{
        private final AnnotatedType<?> annotatedType;
        private final AnnotatedMethod<?> annotatedMethod;
        private final Class<T> returnType;
        private final Class<?> eventType;
        private final Set<Annotation> eventQualifiers;
        private final boolean returnsPublisher;
        private final Method targetMethod;

        ReactorMethod(AnnotatedType<?> annotatedType, AnnotatedMethod<?> annotatedMethod, Class<T> returnType,
                      Class<?> eventType, Set<Annotation> eventQualifiers, boolean returnsPublisher) {
            this.annotatedType = annotatedType;
            this.annotatedMethod = annotatedMethod;
            this.returnType = returnType;
            this.eventType = eventType;
            this.eventQualifiers = findQualifiers(eventQualifiers);
            this.returnsPublisher = returnsPublisher;
            try {
                this.targetMethod = locateMethod();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Unable to locate method",e);
            }
        }

        private Method locateMethod() throws NoSuchMethodException {
            Class<?>[] params = this.annotatedMethod.getJavaMember().getParameterTypes();
            String name = this.annotatedMethod.getJavaMember().getName();
            return annotatedType.getJavaClass().getMethod(name, params);
        }

        boolean matches(Class<?> returnType, Class<?> eventType, Set<Annotation> eventQualifiers) {
            if(!(this.returnType.equals(returnType) && this.eventType.equals(eventType))) {
                return false;
            }
            else if(this.eventQualifiers.isEmpty() && !eventQualifiers.isEmpty()) {
                return false;
            }
            else if(eventQualifiers.isEmpty()) {
                return true;
            }
            else if(this.eventQualifiers.containsAll(eventQualifiers)) {
                return true;
            }
            return false;
        }

        @Override
        public Publisher<T> get(Object event) {
            CDI<Object> cdi = CDI.current();
            List<InstanceHolder> instanceHolders = new ArrayList<>();
            List<Object> parameters = new ArrayList<>();
            for(AnnotatedParameter<?> ap : annotatedMethod.getParameters()) {
                if(ap.isAnnotationPresent(ObservesReactor.class)) {
                    parameters.add(event);
                } else {
                    InstanceHolder holder = getReference(cdi, ap.getJavaParameter().getType(), ap.getAnnotations());
                    instanceHolders.add(holder);
                    parameters.add(holder.instance);
                }
            }
            InstanceHolder eventReceiver = getReference(cdi,annotatedType.getJavaClass(),
                    findQualifiers(annotatedType.getAnnotations()));
            Object[] params = parameters.toArray();
            try {
                Object result = targetMethod.invoke(eventReceiver.instance, params);
                if(returnsPublisher) {
                    return (Publisher<T>) result;
                } else {
                    return Mono.just((T)result).doAfterTerminate(() -> instanceHolders.forEach(InstanceHolder::destroy));
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                return Mono.fromSupplier(() -> { throw new RuntimeException(e); });
            } finally {
                eventReceiver.destroy();
            }
        }

        private InstanceHolder getReference(CDI<Object> cdi, Class<?> type, Set<Annotation> annotations) {
            Annotation[] qualifiers = annotations.toArray(new Annotation[annotations.size()]);
            Instance<Object> handler = (Instance<Object>)cdi.select(type, qualifiers);
            Object instance = handler.get();
            return new InstanceHolder(instance, handler);
        }
    }

    private static class InstanceHolder {
        private Object instance;
        private Instance<Object> holder;

        InstanceHolder(Object instance, Instance<Object> holder) {
            this.instance = instance;
            this.holder = holder;
        }

        void destroy() {
            holder.destroy(instance);
        }
    }

    static Set<Annotation> findQualifiers(Set<Annotation> annotations) {
        Set<Annotation> results = new LinkedHashSet<>();
        for(Annotation annotation : annotations) {
            Class<? extends Annotation> annotationClass = annotation.getClass();
            if(annotation instanceof Default) {
                continue;
            } else if(annotationClass.getAnnotation(Qualifier.class) != null) {
                results.add(annotation);
            } else if(annotationClass.getAnnotation(Stereotype.class) != null) {
                Set<Annotation> parentAnnotations = new LinkedHashSet<>(asList(annotationClass.getAnnotations()));
                results.addAll(findQualifiers(parentAnnotations));
            }
        }
        return results;
    }
}

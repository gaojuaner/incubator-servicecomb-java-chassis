/*
 * Copyright 2017 Huawei Technologies Co., Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.huawei.paas.cse.bizkeeper;

import io.servicecomb.core.Invocation;
import io.servicecomb.core.Response;
import com.netflix.hystrix.HystrixObservable;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action0;
import rx.subjects.ReplaySubject;

public class BizkeeperHandlerDelegate {

    private BizkeeperHandler handler;

    public BizkeeperHandlerDelegate(BizkeeperHandler handler) {
        this.handler = handler;
    }

    protected HystrixObservable<Response> createBizkeeperCommand(Invocation invocation) {
        if (Configuration.INSTANCE.isFallbackForce(handler.groupname,
                invocation.getMicroserviceName(),
                invocation.getOperationMeta().getMicroserviceQualifiedName())) {
            return forceFallbackCommand(invocation);
        }
        return handler.createBizkeeperCommand(invocation);
    }

    protected HystrixObservable<Response> forceFallbackCommand(Invocation invocation) {
        return new HystrixObservable<Response>() {
            @Override
            public Observable<Response> observe() {
                ReplaySubject<Response> subject = ReplaySubject.create();
                final Subscription sourceSubscription = toObservable().subscribe(subject);
                return subject.doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        sourceSubscription.unsubscribe();
                    }
                });
            }

            @Override
            public Observable<Response> toObservable() {
                return Observable.create(f -> {
                    try {
                        if (Configuration.FALLBACKPOLICY_POLICY_RETURN
                                .equals(Configuration.INSTANCE.getFallbackPolicyPolicy(handler.groupname,
                                        invocation.getMicroserviceName(),
                                        invocation.getOperationMeta().getMicroserviceQualifiedName()))) {
                            f.onNext(Response.succResp(null));
                        } else {
                            f.onNext(Response.failResp(invocation.getInvocationType(), BizkeeperExceptionUtils
                                    .createBizkeeperException(BizkeeperExceptionUtils.CSE_HANDLER_BK_FALLBACK,
                                            null,
                                            invocation.getOperationMeta().getMicroserviceQualifiedName())));
                        }
                    } catch (Exception e) {
                        f.onError(e);
                    }
                });
            };
        };
    }

}
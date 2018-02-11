/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel.intent.aws.securitygroup

import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.exceptions.DeclarativeException
import com.netflix.spinnaker.keel.intent.notFound
import com.netflix.spinnaker.keel.intent.securitygroup.SecurityGroupLoader
import com.netflix.spinnaker.keel.intent.securitygroup.SecurityGroupSpec
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
class AmazonSecurityGroupLoader(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache
) : SecurityGroupLoader {

  override fun <S : SecurityGroupSpec> supports(spec: S) = spec is AmazonSecurityGroupSpec

  override fun <S : SecurityGroupSpec> load(spec: S): SecurityGroup? {
    if (spec !is AmazonSecurityGroupSpec) {
      // TODO rz
      throw DeclarativeException("this should never happen")
    }

    try {
      return if (spec.vpcName == null) {
        cloudDriverService.getSecurityGroup(spec.accountName, "aws", spec.name, spec.region)
      } else {
        cloudDriverService.getSecurityGroup(
          spec.accountName,
          "aws",
          spec.name,
          spec.region,
          cloudDriverCache.networkBy(
            spec.vpcName,
            spec.accountName,
            spec.region
          ).id
        )
      }
    } catch (e: RetrofitError) {
      if (e.notFound()) {
        return null
      }
      // TODO rz - need a wrapper around this guy
      throw e
    }
  }

  override fun <S : SecurityGroupSpec> upstreamGroup(spec: S, name: String): SecurityGroup? {
    if (spec !is AmazonSecurityGroupSpec) {
      // TODO rz
      throw DeclarativeException("this should never happen")
    }

    try {
      return cloudDriverService.getSecurityGroup(
        spec.accountName,
        "aws",
        name,
        spec.region,
        cloudDriverCache.networkBy(
          spec.vpcName!!,
          spec.accountName,
          spec.region
        ).id
      )
    } catch (e: RetrofitError) {
      if (e.notFound()) {
        return null
      }
      // TODO rz - this isn't the right thing to do
      throw e
    }
  }
}

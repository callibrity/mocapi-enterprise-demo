/*
 * Copyright © 2026 Callibrity, Inc. (contactus@callibrity.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.callibrity.mocapi.demo.catalog.repository;

import com.callibrity.mocapi.demo.catalog.domain.LifecycleStage;
import com.callibrity.mocapi.demo.catalog.domain.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRepository extends JpaRepository<Service, UUID> {

  Optional<Service> findByName(String name);

  Page<Service> findAllByOwnerIsNull(Pageable pageable);

  @Query(
      """
            select s from Service s
            where (:domain is null or s.domain = :domain)
              and (:lifecycle is null or s.lifecycleStage = :lifecycle)
              and (:tag is null or :tag member of s.tags)
            """)
  Page<Service> search(
      @Param("domain") String domain,
      @Param("lifecycle") LifecycleStage lifecycle,
      @Param("tag") String tag,
      Pageable pageable);

  @Query(
      """
            select s from Service s
            where s.lifecycleStage = com.callibrity.mocapi.demo.catalog.domain.LifecycleStage.DEPRECATED
              and exists (select 1 from Dependency d where d.toService = s)
            """)
  Page<Service> findDeprecatedInUse(Pageable pageable);

  List<Service> findAllByOwnerName(String teamName);
}

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
package com.callibrity.mocapi.demo.catalog.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jwcarman.jpa.entity.BaseEntity;

@Entity
@Table(name = "service")
@Getter
@Setter
@NoArgsConstructor
public class Service extends BaseEntity {

  @Column(nullable = false, unique = true)
  private String name;

  @Column(nullable = false)
  private String displayName;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false)
  private String domain;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "owner_id")
  private Team owner;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LifecycleStage lifecycleStage = LifecycleStage.ACTIVE;

  private String repoUrl;

  private String runbookUrl;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "service_tag", joinColumns = @JoinColumn(name = "service_id"))
  @Column(name = "tag")
  private Set<String> tags = new LinkedHashSet<>();

  public void setTags(Set<String> tags) {
    this.tags = tags == null ? new LinkedHashSet<>() : new LinkedHashSet<>(tags);
  }
}


/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.security.internal;

import java.util.Arrays;
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.repository.security.ContentPermissionChecker;
import org.sonatype.nexus.repository.security.RepositoryContentSelectorPermission;
import org.sonatype.nexus.repository.security.RepositoryViewPermission;
import org.sonatype.nexus.security.SecurityHelper;
import org.sonatype.nexus.selector.SelectorConfiguration;
import org.sonatype.nexus.selector.SelectorEvaluationException;
import org.sonatype.nexus.selector.SelectorEvaluator;
import org.sonatype.nexus.selector.VariableSource;

import com.google.common.annotations.VisibleForTesting;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @since 3.1
 */
@Named
@Singleton
public class ContentPermissionCheckerImpl
    extends ComponentSupport
    implements ContentPermissionChecker
{
  private final SecurityHelper securityHelper;

  private final SelectorEvaluator selectorEvaluator;

  @Inject
  public ContentPermissionCheckerImpl(final SecurityHelper securityHelper, final SelectorEvaluator selectorEvaluator) {
    this.securityHelper = checkNotNull(securityHelper);
    this.selectorEvaluator = checkNotNull(selectorEvaluator);
  }

  @VisibleForTesting
  public boolean isViewPermitted(final String repositoryName, final String repositoryFormat, final String action) {
    return securityHelper.anyPermitted(new RepositoryViewPermission(repositoryFormat, repositoryName, action));
  }

  @VisibleForTesting
  public boolean isContentPermitted(final String repositoryName,
                                    final String repositoryFormat,
                                    final String action,
                                    final SelectorConfiguration selectorConfiguration,
                                    final VariableSource variableSource)
  {
    try {
      if (selectorEvaluator.evaluate(selectorConfiguration, variableSource)) {
        RepositoryContentSelectorPermission perm = new RepositoryContentSelectorPermission(
            selectorConfiguration.getName(), repositoryFormat, repositoryName, Arrays.asList(action));

        //any single passing permission will cause success
        if (securityHelper.anyPermitted(perm)) {
          return true;
        }
      }
    }
    catch (SelectorEvaluationException e) {
      if (log.isTraceEnabled()) {
        log.debug(e.getMessage(), e);
      }
      else {
        log.debug(e.getMessage());
      }
    }

    return false;
  }

  @Override
  public boolean isPermitted(final String repositoryName,
                             final String repositoryFormat,
                             final String action,
                             final Collection<SelectorConfiguration> selectorConfigurations,
                             final VariableSource variableSource)
  {
    //check view perm first, if applicable, grant access
    if (isViewPermitted(repositoryName, repositoryFormat, action)) {
      return true;
    }
    //otherwise check the content selector perms
    return selectorConfigurations.stream()
        .anyMatch(config -> isContentPermitted(repositoryName, repositoryFormat, action, config, variableSource));
  }
}

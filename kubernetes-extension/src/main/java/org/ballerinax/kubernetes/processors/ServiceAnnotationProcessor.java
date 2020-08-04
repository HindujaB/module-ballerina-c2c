/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinax.kubernetes.processors;

import org.ballerinalang.model.tree.AnnotationAttachmentNode;
import org.ballerinalang.model.tree.ServiceNode;
import org.ballerinalang.model.tree.SimpleVariableNode;
import org.ballerinax.kubernetes.exceptions.KubernetesPluginException;
import org.ballerinax.kubernetes.models.KubernetesContext;
import org.ballerinax.kubernetes.models.ServiceModel;
import org.wso2.ballerinalang.compiler.tree.BLangIdentifier;
import org.wso2.ballerinalang.compiler.tree.BLangService;
import org.wso2.ballerinalang.compiler.tree.BLangSimpleVariable;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeInit;
import org.wso2.ballerinalang.compiler.tree.types.BLangUserDefinedType;

import java.util.List;

import static org.ballerinax.kubernetes.KubernetesConstants.SVC_POSTFIX;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.convertRecordFields;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.getValidName;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.isBlank;

/**
 * Service annotation processor.
 */
public class ServiceAnnotationProcessor extends AbstractAnnotationProcessor {

    @Override
    public void processAnnotation(ServiceNode serviceNode, AnnotationAttachmentNode attachmentNode) throws
            KubernetesPluginException {
        BLangService bService = (BLangService) serviceNode;
        for (BLangExpression attachedExpr : bService.getAttachedExprs()) {
            // If not anonymous endpoint throw error.
            if (attachedExpr instanceof BLangSimpleVarRef) {
                throw new KubernetesPluginException("adding @kubernetes:Service{} annotation to a service is only " +
                        "supported when the service has an anonymous listener");
            }
        }
        ServiceModel serviceModel = new ServiceModel();
        if (isBlank(serviceModel.getName())) {
            serviceModel.setName(getValidName(serviceNode.getName().getValue()) + SVC_POSTFIX);
        }

        BLangTypeInit bListener = (BLangTypeInit) bService.getAttachedExprs().get(0);
        validatePorts(serviceModel, bListener);

        KubernetesContext.getInstance().getDataHolder().addBListenerToK8sServiceMap(serviceNode.getName().getValue(),
                serviceModel);
    }

    private void validatePorts(ServiceModel serviceModel, BLangTypeInit bListener) throws KubernetesPluginException {
        // If service annotation port is not empty, then listener port is used for the k8s svc target port while
        // service annotation port is used for k8s port.
        // If service annotation port is empty, then listener port is used for both port and target port of the k8s
        // svc.
        if (serviceModel.getPort() == -1) {
            serviceModel.setPort(extractPort(bListener));
        }

        if (serviceModel.getTargetPort() == -1) {
            serviceModel.setTargetPort(extractPort(bListener));
        }
        setServiceProtocol(serviceModel, bListener);

        // Validate service type is set to NodePort when a NodePort is defined.
        if (serviceModel.getNodePort() != -1 && !"NodePort".equals(serviceModel.getServiceType())) {
            throw new KubernetesPluginException("NodePort [" + serviceModel.getNodePort() +
                    "] defined without setting the service type to NodePort. " +
                    "Found [" + serviceModel.getServiceType() + "]");
        }
    }

    @Override
    public void processAnnotation(SimpleVariableNode variableNode, AnnotationAttachmentNode attachmentNode)
            throws KubernetesPluginException {
        ServiceModel serviceModel = new ServiceModel();
        if (isBlank(serviceModel.getName())) {
            serviceModel.setName(getValidName(variableNode.getName().getValue()) + SVC_POSTFIX);
        }

        BLangTypeInit bListener = (BLangTypeInit) ((BLangSimpleVariable) variableNode).expr;
        validatePorts(serviceModel, bListener);
        KubernetesContext.getInstance().getDataHolder().addBListenerToK8sServiceMap(variableNode.getName().getValue()
                , serviceModel);
    }

    private int extractPort(BLangTypeInit bListener) throws KubernetesPluginException {
        try {
            if ("int".equals(bListener.argsExpr.get(0).expectedType.toString())) {
                // Listener with port as the first argument. eg:- http listener
                return Integer.parseInt(bListener.argsExpr.get(0).toString());
            } else {
                // other listeners with port as an attribute
                for (BLangRecordLiteral.BLangRecordKeyValueField arg :
                        convertRecordFields(((BLangRecordLiteral) bListener.argsExpr.get(0)).getFields())) {
                    if ("port".equals(arg.getKey().toString())) {
                        return Integer.parseInt(arg.getValue().toString());
                    }
                }
            }
            throw new KubernetesPluginException("unable extract port from the listener " +
                    bListener.argsExpr.get(0).toString());
        } catch (NumberFormatException e) {
            throw new KubernetesPluginException("unable to parse port/targetPort for the service: " +
                    bListener.argsExpr.get(0).toString());
        }
    }

    private void setServiceProtocol(ServiceModel serviceModel, BLangTypeInit bListener) {
        if (null != bListener.userDefinedType) {
            BLangUserDefinedType userDefinedType = (BLangUserDefinedType) bListener.userDefinedType;
            serviceModel.setProtocol(userDefinedType.getPackageAlias().getValue());
        } else {
            BLangIdentifier packageAlias =
                    ((BLangUserDefinedType) ((BLangSimpleVariable) bListener.parent).typeNode).getPackageAlias();
            serviceModel.setProtocol(packageAlias.getValue());
        }
        if ("http".equals(serviceModel.getProtocol())) {
            // Add http config
            if (bListener.argsExpr.size() == 2) {
                if (bListener.argsExpr.get(1) instanceof BLangRecordLiteral) {
                    BLangRecordLiteral bConfigRecordLiteral = (BLangRecordLiteral) bListener.argsExpr.get(1);
                    List<BLangRecordLiteral.BLangRecordKeyValueField> listenerConfig =
                            convertRecordFields(bConfigRecordLiteral.getFields());
                    serviceModel.setProtocol(isHTTPS(listenerConfig) ? "https" : "http");
                }
            }
        }
    }

    private boolean isHTTPS(List<BLangRecordLiteral.BLangRecordKeyValueField> listenerConfig) {
        for (BLangRecordLiteral.BLangRecordKeyValueField keyValue : listenerConfig) {
            String key = keyValue.getKey().toString();
            if ("secureSocket".equals(key)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Enum for Service configurations.
     */
    public enum ServiceConfiguration {
        name,
        labels,
        annotations,
        serviceType,
        port,
        sessionAffinity
    }
}

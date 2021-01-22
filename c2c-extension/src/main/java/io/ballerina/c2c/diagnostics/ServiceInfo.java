package io.ballerina.c2c.diagnostics;

import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Service of a ballerina document.
 *
 * @since 2.0.0
 */
public class ServiceInfo {
    private ServiceDeclarationNode node;
    private String serviceName;
    private ListenerInfo listener;
    private List<ResourceInfo> resourceInfo;

    public ServiceInfo(ListenerInfo listener, ServiceDeclarationNode node, String serviceName) {
        this.listener = listener;
        this.node = node;
        this.serviceName = serviceName;
        this.resourceInfo = new ArrayList<>();
    }

    public ServiceDeclarationNode getNode() {
        return node;
    }

    public String getServiceName() {
        return serviceName;
    }

    public ListenerInfo getListener() {
        return listener;
    }

    public List<ResourceInfo> getResourceInfo() {
        return resourceInfo;
    }

    public void setNode(ServiceDeclarationNode node) {
        this.node = node;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setListener(ListenerInfo listener) {
        this.listener = listener;
    }

    public void setResourceInfo(List<ResourceInfo> resourceInfo) {
        this.resourceInfo = resourceInfo;
    }

    public void addResource(ResourceInfo resourceInfo) {
        this.resourceInfo.add(resourceInfo);
    }

    @Override
    public String toString() {
        return "ServiceInfo{" +
                ", serviceName='" + serviceName + '\'' +
                ", listener=" + listener +
                ", resourceInfo=" + resourceInfo +
                '}';
    }
}

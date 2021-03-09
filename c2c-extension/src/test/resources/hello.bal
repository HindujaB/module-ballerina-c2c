import ballerina/http;
import ballerinax/kubernetes;

@kubernetes:Deployment {
    livenessProbe: true
}
@kubernetes:Ingress {
    hostname: "abc.com"
}
@kubernetes:Service {
    name: "hello",
    serviceType: "NodePort"
}
listener http:Listener helloEP = new(9090, config = {
    secureSocket: {
        keyKey: {
            path: "${ballerina.home}/bre/security/ballerinaKeystore.p12",
            password: "ballerina"
        }
    }
});

@http:ServiceConfig {
    basePath: "/helloWorld"
}
service helloWorld on helloEP {
    resource function sayHello(http:Caller outboundEP, http:Request request) {
        http:Response response = new;
        response.setTextPayload("Hello, World from service helloWorld ! \n");
        checkpanic outboundEP->respond(response);
    }
}

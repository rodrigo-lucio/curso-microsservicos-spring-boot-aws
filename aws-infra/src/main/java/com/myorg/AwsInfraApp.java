package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

import java.util.Arrays;

public class AwsInfraApp {
    public static void main(final String[] args) {
        App app = new App();
        VpcStack vpcStack = new VpcStack(app, "Vpc");

        ClusterStack clusterStack = new ClusterStack(app, "Cluster", vpcStack.getVpc());
        clusterStack.addDependency(vpcStack);

        RdsStack rdsStack = new RdsStack(app, "Rds", vpcStack.getVpc());
        rdsStack.addDependency(vpcStack);

        SnsStack snsStack = new SnsStack(app, "Sns");

        InvoiceAppStack invoiceAppStack = new InvoiceAppStack(app, "InvoiceApp");

        Service01Stack service01Stack = new Service01Stack(app, "Service01", clusterStack.getCluster(),
                snsStack.getProductEventsTopic(), invoiceAppStack.getBucket(), invoiceAppStack.getS3InvoiceQueue());
        service01Stack.addDependency(clusterStack);
        service01Stack.addDependency(rdsStack);
        service01Stack.addDependency(snsStack);
        service01Stack.addDependency(invoiceAppStack);

        DynamoDbStack dynamoDbStack = new DynamoDbStack(app, "DynamoDb");

        Service02Stack service02Stack = new Service02Stack(app, "Service02", clusterStack.getCluster(),
                snsStack.getProductEventsTopic(), dynamoDbStack.getProducEventsTable());
        service02Stack.addDependency(snsStack);
        service02Stack.addDependency(clusterStack);
        service02Stack.addDependency(dynamoDbStack);


        app.synth();
    }
}


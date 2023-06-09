package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.events.targets.SnsTopic;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.Map;

public class Service01Stack extends Stack {

    public static final String ACTUATOR_HEALTH = "/actuator/health";
    public static final String ACTUATOR_PORT = "8080";

    public Service01Stack(final Construct scope, final String id, Cluster cluster, SnsTopic productEventsTopic, Bucket invoicebucket, Queue invoiceQueue) {
        this(scope, id, null, cluster, productEventsTopic, invoicebucket, invoiceQueue);
    }

    public Service01Stack(final Construct scope, final String id, final StackProps props, Cluster cluster, SnsTopic productEventsTopic, Bucket invoicebucket, Queue invoiceQueue) {
        super(scope, id, props);

        Map<String, String> environments = new HashMap<>();
        environments.put("SPRING_DATASOURCE_URL", "jdbc:mysql://" + Fn.importValue("rds-endpoint") + ":3306/aws_project01?createDatabaseIfNotExist=true");
        environments.put("SPRING_DATASOURCE_USERNAME", "admin");
        environments.put("SPRING_DATASOURCE_PASSWORD",  Fn.importValue("rds-password"));
        environments.put("AWS_REGION",  "us-east-1");
        environments.put("AWS_SNS_TOPIC_PRODUCT_EVENTS_ARN", productEventsTopic.getTopic().getTopicArn());
        environments.put("AWS_S3_BUCKET_INVOICE_NAME", invoicebucket.getBucketName());
        environments.put("AWS_SQS_QUEUE_INVOICE_EVENTS_NAME", invoiceQueue.getQueueName());

        ApplicationLoadBalancedFargateService service01 = ApplicationLoadBalancedFargateService.Builder
                .create(this, "ALB01")
                .serviceName("service-01")
                .cluster(cluster)
                .cpu(512)
                .memoryLimitMiB(1024)
                .desiredCount(2)
                .listenerPort(8080)
                .assignPublicIp(true)
                .taskImageOptions(
                        ApplicationLoadBalancedTaskImageOptions.builder()
                                .containerName("aws_project01")
                                .image(ContainerImage.fromRegistry("rodrigolucio/service01:0.0.10"))
                                .containerPort(8080)
                                .logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                                .logGroup(LogGroup.Builder.create(this, "Service01LogGroup")
                                                        .logGroupName("Service01")
                                                        .removalPolicy(RemovalPolicy.DESTROY)
                                                        .build())
                                        .streamPrefix("Service01")
                                        .build()))
                                .environment(environments)
                                .build())
                .publicLoadBalancer(true)
                .build();

            service01.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                    .path(ACTUATOR_HEALTH)
                    .port(ACTUATOR_PORT)
                    .healthyHttpCodes("200")
                    .build());

        // Configurações do auto scaling
        ScalableTaskCount scalableTaskCount = service01.getService().autoScaleTaskCount(EnableScalingProps.builder()
                        .minCapacity(2) //Min 2 instâncias da aplicação
                        .maxCapacity(4) //Max 4 instâncias da aplicação
                .build());

        //Se o limite de CPU ultrapassar 50% num intervalo de 60s então ele cria uma nova instancia, no limite maximo de 4 instâncias
        //Se durante 60s, tiver um limite de CPU abaixo de 50%, ele destroi as instancias que foram criadas automaticamente
        scalableTaskCount.scaleOnCpuUtilization("Service01AutoScaling", CpuUtilizationScalingProps.builder()
                        .targetUtilizationPercent(50)
                        .scaleInCooldown(Duration.seconds(60))
                        .scaleOutCooldown(Duration.seconds(60))
                .build());

        //define que o meu serviço pode publicar mensagens no meu topico
        productEventsTopic.getTopic().grantPublish(service01.getTaskDefinition().getTaskRole());

        invoiceQueue.grantConsumeMessages(service01.getTaskDefinition().getTaskRole());
        invoicebucket.grantReadWrite(service01.getTaskDefinition().getTaskRole());

    }
}

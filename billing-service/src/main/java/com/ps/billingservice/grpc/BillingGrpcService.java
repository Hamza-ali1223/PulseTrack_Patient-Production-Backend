package com.ps.billingservice.grpc;


import billing.*;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class BillingGrpcService extends BillingServiceGrpc.BillingServiceImplBase
{
    private static final Logger log = LoggerFactory.getLogger(BillingGrpcService.class);

    @Override
    public void createBillingAccount(BillingRequest billingRequest, StreamObserver<BillingResponse> responseObserver)
    {
        log.info("Create Billing Account request received {}", billingRequest.toString());

        //Business Logic e.g save to db, performs calculates etc
        BillingResponse response = BillingResponse.newBuilder()
                .setAccountId(billingRequest.getPatientId())
                .setStatus("Active Response")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}

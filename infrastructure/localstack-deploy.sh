#!/usr/bin/env bash

set -euo pipefail

STACK_NAME="patient-management"
TEMPLATE_FILE="F:\Javac\Patient-Production-Backend\infrastructure\cdk.out\localstack.template.json"
ENDPOINT="http://localhost:4566"

echo "===================================================="
echo "🚀 Deploying CloudFormation Stack to Localstack"
echo "===================================================="
echo "Stack Name   : $STACK_NAME"
echo "Template File: $TEMPLATE_FILE"
echo "Endpoint     : $ENDPOINT"
echo ""

echo "⏳ Starting deployment..."
aws --endpoint-url=$ENDPOINT cloudformation deploy \
    --stack-name $STACK_NAME \
    --template-file "$TEMPLATE_FILE" \
    --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
    --no-fail-on-empty-changeset \
    --output text || {
        echo "❌ Deployment failed. Fetching stack events..."
        aws --endpoint-url=$ENDPOINT cloudformation describe-stack-events \
            --stack-name $STACK_NAME \
            --output table
        exit 1
    }

echo ""
echo "===================================================="
echo "📡 Deployment triggered. Fetching stack events..."
echo "===================================================="

# Print CloudFormation events for debugging
aws --endpoint-url=$ENDPOINT cloudformation describe-stack-events \
    --stack-name $STACK_NAME \
    --output table

echo ""
echo "===================================================="
echo "📊 Checking stack status..."
echo "===================================================="

STATUS=$(aws --endpoint-url=$ENDPOINT cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --query "Stacks[0].StackStatus" \
    --output text)

echo "Current Stack Status: $STATUS"

if [[ "$STATUS" != "CREATE_COMPLETE" && "$STATUS" != "UPDATE_COMPLETE" ]]; then
    echo "⚠️ Stack is not fully deployed. Showing latest failure reasons:"
    echo ""
    aws --endpoint-url=$ENDPOINT cloudformation describe-stack-events \
        --stack-name $STACK_NAME \
        --max-items 10 \
        --query 'StackEvents[].[Timestamp,ResourceStatus,ResourceType,LogicalResourceId,ResourceStatusReason]' \
        --output table
fi

echo ""
echo "===================================================="
echo "🌐 Checking for Load Balancer (if exists)..."
echo "===================================================="

LB_DNS=$(aws --endpoint-url=$ENDPOINT elbv2 describe-load-balancers \
    --query "LoadBalancers[0].DNSName" \
    --output text 2>/dev/null || echo "NONE")

echo "ALB DNS Name: $LB_DNS"

echo ""
echo "===================================================="
echo "🎉 Done!"
echo "===================================================="

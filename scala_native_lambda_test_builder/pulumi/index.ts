import * as pulumi from "@pulumi/pulumi";
import * as aws from "@pulumi/aws";
import {RoleArgs, RolePolicyAttachmentArgs} from "@pulumi/aws/iam";
import {EventSourceMappingArgs, FunctionArgs} from "@pulumi/aws/lambda";
import {Role} from "@pulumi/aws/iam/role";
import {RolePolicyAttachment} from "@pulumi/aws/iam/rolePolicyAttachment";

//Create an AWS resource (S3 Bucket)
// const bucket = new aws.s3.Bucket( "pbbucket1", {bucket: "pbbucket1"} );
//
// // Export the name of the bucket
// export const bucketName = bucket.id;


const sqs = new aws.sqs.Queue( "test-queue", {name: "test-queue"} )

//Extracting things out and adding explicit types makes things a bit nicer to work with
//as navigating to the specification is easy, also autocomplete is nicer.
//Miss case classes for this stuff as dictionary structures are not very fun
//without a lot of dancing.
const assumeRolePolicy : aws.iam.PolicyDocument = {
    "Version":   "2012-10-17",
    "Statement": [
        {
            "Effect":    "Allow",
            "Principal": {
                "Service": "lambda.amazonaws.com"
            },
            "Action":    "sts:AssumeRole"
        }
    ]
}

const lambdaRoleArgs : RoleArgs = {
    name:             "lambda-role",
    assumeRolePolicy: assumeRolePolicy,
    path:             "/"
    /*,
    managedPolicyArns: ["arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"]*/
}


const lambdaRole : Role = new aws.iam.Role( "lambda-role", lambdaRoleArgs )

function attachPolicyToRole( role : Role,
                             attachmentName : string,
                             policyArnValue : pulumi.Input<string> ) : aws.iam.RolePolicyAttachment {
    const lambdaRolePolicyAttachmentArgs : RolePolicyAttachmentArgs = {
        role:      role.name,
        policyArn: policyArnValue
    }

    return new aws.iam.RolePolicyAttachment(
        attachmentName,
        lambdaRolePolicyAttachmentArgs
    );
}

const executionPolicyAttachment : RolePolicyAttachment = attachPolicyToRole(
    lambdaRole,
    "lambda-role-execution-attachment",
    "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
)

const sqsPolicyAttachment : RolePolicyAttachment = attachPolicyToRole(
    lambdaRole,
    "lambda-role-sqs-attachment",
    "arn:aws:iam::aws:policy/service-role/AWSLambdaSQSQueueExecutionRole"
)

let functionArgs : FunctionArgs = {
    code:    new pulumi.asset.FileArchive( "../deployable/scala_native_lambda.zip" ),
    name:    "test-lambda",
    role:    lambdaRole.arn,
    runtime: "provided.al2",
    handler: "moomins"
};

const lambda = new aws.lambda.Function( "test-lambda", functionArgs )

const sqsEventSourceMappingArgs : EventSourceMappingArgs = {
    eventSourceArn: sqs.arn,
    enabled:        true,
    functionName:   lambda.arn,
    batchSize:      1
}

const sqsMapping = new aws.lambda.EventSourceMapping( "test-lambda-sqs-trigger", sqsEventSourceMappingArgs )
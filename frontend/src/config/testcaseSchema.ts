/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
// TestCase form schema, transcribed verbatim from the legacy config-tool's `var schema`
// (alldep-jar/src/main/resources/gatf-config-tool/index.js, used by addTestCase()).
// Drives the structured .xml API test-case editor. Reuses CfgSchema/SchemaForm from Slice B.

import type { CfgSchema } from './gatfConfigSchema';

export const testcaseSchema: CfgSchema = {
  "type": "object",
  "properties": {
    "baseUrl": {
      "type": "string"
    },
    "url": {
      "type": "string"
    },
    "name": {
      "type": "string"
    },
    "method": {
      "type": "string",
      "enum": [
        "GET",
        "POST",
        "PUT",
        "DELETE"
      ]
    },
    "description": {
      "type": "string"
    },
    "content": {
      "type": "string",
      "ui": "textarea"
    },
    "headers": {
      "type": "map",
      "types": {
        "key": {
          "type": "string",
          "defaultIndex": 1,
          "enum": [
            "",
            "Accept",
            "Accept-Charset",
            "Accept-Encoding",
            "Accept-Language",
            "Allow",
            "Authorization",
            "Cache-Control",
            "Content-Disposition",
            "Content-Encoding",
            "Content-ID",
            "Content-Language",
            "Content-Length",
            "Content-Location",
            "Content-Type",
            "Date",
            "ETag",
            "Expires",
            "Host",
            "If-Match",
            "If-Modified-Since",
            "If-None-Match",
            "If-Unmodified-Since",
            "Last-Modified",
            "Location",
            "Link",
            "Retry-After",
            "User-Agent",
            "Vary",
            "WWW-Authenticate",
            "Cookie",
            "Set-Cookie"
          ]
        },
        "value": {
          "type": "string"
        }
      }
    },
    "exQueryPart": {
      "type": "string"
    },
    "expectedResCode": {
      "type": "integer",
      "defaultIndex": 0,
      "enum": [
        200,
        201,
        202,
        204,
        205,
        206,
        301,
        302,
        303,
        304,
        305,
        307,
        400,
        401,
        402,
        403,
        404,
        405,
        406,
        407,
        408,
        409,
        410,
        411,
        412,
        413,
        414,
        415,
        416,
        417,
        500,
        501,
        502,
        503,
        504,
        505
      ]
    },
    "expectedResContentType": {
      "type": "string"
    },
    "contract": {
      "type": "string"
    },
    "contractHash": {
      "type": "string"
    },
    "expectedResContent": {
      "type": "string",
      "ui": "textarea"
    },
    "skipTest": {
      "type": "boolean",
      "required": true
    },
    "detailedLog": {
      "type": "boolean",
      "required": true
    },
    "secure": {
      "type": "boolean",
      "required": true
    },
    "expectedNodes": {
      "type": "array",
      "items": {
        "nolabel": true,
        "type": "string"
      }
    },
    "soapBase": {
      "type": "boolean",
      "required": true
    },
    "wsdlKey": {
      "type": "string"
    },
    "operationName": {
      "type": "string"
    },
    "soapParameterValues": {
      "type": "map",
      "types": {
        "key": {
          "type": "string"
        },
        "value": {
          "type": "string"
        }
      }
    },
    "workflowContextParameterMap": {
      "type": "map",
      "types": {
        "key": {
          "type": "string"
        },
        "value": {
          "type": "string"
        }
      }
    },
    "sequence": {
      "type": "integer"
    },
    "multipartContent": {
      "type": "array",
      "items": {
        "nolabel": true,
        "type": "string"
      }
    },
    "outFileName": {
      "type": "string"
    },
    "repeatScenarioProviderName": {
      "type": "string",
      "defaultIndex": 0,
      "enumVar": "miscMap.providers"
    },
    "numberOfExecutions": {
      "type": "integer"
    },
    "repeatScenariosConcurrentExecution": {
      "type": "boolean",
      "required": true
    },
    "stopOnFirstFailureForPerfTest": {
      "type": "boolean",
      "required": true
    },
    "simulationNumber": {
      "type": "integer"
    },
    "preWaitMs": {
      "type": "number"
    },
    "postWaitMs": {
      "type": "number"
    },
    "reportResponseContent": {
      "type": "boolean",
      "defaultIndex": 1
    },
    "preExecutionDataSourceHookName": {
      "type": "string",
      "defaultIndex": 0,
      "enumVar": "miscMap.hooks"
    },
    "postExecutionDataSourceHookName": {
      "type": "string",
      "defaultIndex": 0,
      "enumVar": "miscMap.hooks"
    },
    "abortOnInvalidStatusCode": {
      "type": "boolean",
      "required": true
    },
    "abortOnInvalidContentType": {
      "type": "boolean",
      "required": true
    },
    "relatedTestName": {
      "type": "string",
      "defaultIndex": 0,
      "enumVar": "currtestcases"
    },
    "executeOnCondition": {
      "type": "string"
    },
    "logicalValidations": {
      "type": "array",
      "items": {
        "nolabel": true,
        "type": "string"
      }
    },
    "perfConfig": {
      "label": {
        "type": "section",
        "value": "Performance Test Details"
      },
      "type": "object",
      "properties": {
        "type": {
          "type": "string",
          "enum": [
            "none",
            "wrk",
            "wrk2",
            "vegeta",
            "autocannon"
          ]
        },
        "connections": {
          "type": "number"
        },
        "durationSeconds": {
          "type": "number"
        },
        "threads": {
          "type": "number"
        },
        "rate": {
          "type": "number"
        },
        "filePath": {
          "type": "string"
        },
        "timeout": {
          "type": "number"
        },
        "latency": {
          "type": "boolean"
        },
        "extras": {
          "type": "string"
        }
      }
    }
  }
};

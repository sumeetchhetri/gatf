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
// Config schemas transcribed verbatim from the legacy config-tool configuration()/generatorConfig()
// (alldep-jar/src/main/resources/gatf-config-tool/index.js). Drives the schema-driven ConfigView.

export interface CfgSchema {
  type: 'object' | 'array' | 'map' | 'string' | 'integer' | 'number' | 'boolean';
  properties?: Record<string, CfgSchema>;
  items?: CfgSchema;
  types?: { key: CfgSchema; value: CfgSchema };
  label?: { type: string; value: string; border?: boolean };
  enumVar?: string;
  // Q3: a fixed option list rendered as a select (like enumVar). Deliberately NOT the legacy `enum` key: the
  // transcribed testcase schema carries `enum` lists (e.g. method: GET/POST/PUT/DELETE) that were never enforced.
  options?: string[];
  default?: string;
  defaultIndex?: number;
  required?: boolean;
  nolabel?: boolean;
  // legacy schema carries occasional annotations (e.g. a Java class "name"); tolerate them
  [k: string]: unknown;
}

export const executorSchema: CfgSchema = {
  "type": "object",
  "properties": {
    "baseUrl": {
      "type": "string"
    },
    "testCasesBasePath": {
      "type": "string"
    },
    "outFilesBasePath": {
      "type": "string"
    },
    "outFilesDir": {
      "type": "string"
    },
    "autoPauseOnElNotFound": {
      "type": "boolean",
      "default": "true"
    },
    "waitOnWarnException": {
      "type": "number",
      "default": "0"
    },
    "waitOnFailException": {
      "type": "number",
      "default": "0"
    },
    "mailSimulator": {
      "label": {
        "type": "section",
        "value": "Mail Simulator",
        "border": true
      },
      "type": "object",
      "properties": {
        "enabled": {
          "type": "boolean"
        },
        "login": {
          "type": "string"
        },
        "password": {
          "type": "string"
        },
        "isSecure": {
          "type": "boolean"
        },
        "smtpPort": {
          "type": "integer"
        },
        "imapPort": {
          "type": "integer"
        }
      }
    },
    "htttpServerSimulator": {
      "label": {
        "type": "section",
        "value": "HTTP Simulator",
        "border": true
      },
      "type": "object",
      "properties": {
        "enabled": {
          "type": "boolean"
        },
        "port": {
          "type": "integer"
        },
        "headers": {
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
        "body": {
          "type": "string"
        }
      }
    },
    "authEnabled": {
      "type": "boolean",
      "required": true
    },
    "authUrl": {
      "type": "string"
    },
    "authExtractAuth": {
      "type": "string"
    },
    "authParamsDetails": {
      "type": "string"
    },
    "wsdlLocFile": {
      "type": "string"
    },
    "soapAuthEnabled": {
      "type": "boolean",
      "required": true
    },
    "soapAuthWsdlKey": {
      "type": "string"
    },
    "soapAuthOperation": {
      "type": "string"
    },
    "soapAuthExtractAuth": {
      "type": "string"
    },
    "numConcurrentExecutions": {
      "type": "integer"
    },
    "httpCompressionEnabled": {
      "type": "boolean",
      "required": true
    },
    "httpConnectionTimeout": {
      "type": "integer"
    },
    "httpRequestTimeout": {
      "type": "integer"
    },
    "concurrentUserSimulationNum": {
      "type": "integer"
    },
    "testDataConfigFile": {
      "type": "string"
    },
    "authDataProvider": {
      "type": "string",
      "defaultIndex": 1,
      "enumVar": "miscMap.providers"
    },
    "compareEnabled": {
      "type": "boolean",
      "required": true
    },
    "enabled": {
      "type": "boolean"
    },
    "loadTestingEnabled": {
      "type": "boolean",
      "required": true
    },
    "loadTestingTime": {
      "type": "number"
    },
    "distributedLoadTests": {
      "type": "boolean",
      "required": true
    },
    "compareBaseUrlsNum": {
      "type": "integer"
    },
    "concurrentUserRampUpTime": {
      "type": "number"
    },
    "loadTestingReportSamples": {
      "type": "integer"
    },
    "repeatSuiteExecutionNum": {
      "type": "integer"
    },
    "isGenerateExecutionLogs": {
      "type": "boolean",
      "required": false
    },
    "isSeleniumExecutor": {
      "type": "boolean",
      "required": false
    },
    "selConcWebdriver": {
      "type": "boolean",
      "required": false
    },
    "isSeleniumModuleTests": {
      "type": "boolean",
      "required": false
    },
    "seleniumScript": {
      "type": "string",
      "required": false
    },
    "seleniumScriptRetryCount": {
      "type": "integer",
      "required": false
    },
    // Q3: trace capture. traceMode wins over the legacy enableTrace flag when set (blank = use enableTrace).
    "traceMode": {
      "label": {
        "type": "section",
        "value": "Trace (UI runs)"
      },
      "type": "string",
      "options": ["off", "on", "retain-on-failure", "on-first-retry"]
    },
    "traceProtocol": {
      "type": "string",
      "options": ["cdp", "bidi", "hybrid"]
    },
    "traceNetwork": {
      "type": "boolean"
    },
    "traceConsole": {
      "type": "boolean"
    },
    "traceSnapshots": {
      "type": "boolean"
    },
    "traceVideo": {
      "type": "boolean"
    },
    // Q9: e.g. "lcp<2500, requests<80, jsheap<50mb" - evaluated from each UI script's trace; a breach fails the script
    "perfBudgets": {
      "type": "string"
    },
    // Q10: passive security checks over each UI script's trace (report = list findings, fail = fail on medium+)
    "securityChecks": {
      "type": "string",
      "options": ["report", "fail", "off"]
    },
    // M1: wait before each UI action until its element is visible, enabled, stable and not covered.
    // Unset = legacy (no wait); new configs set it on. actionTimeout = ms per action (blank = 5000).
    "autoWait": {
      "label": {
        "type": "section",
        "value": "Auto-wait (UI runs)"
      },
      "type": "boolean"
    },
    "actionTimeout": {
      "type": "integer"
    },
    // M2: ?? value assertions retry until assertTimeout ms (blank = 5000). Unset = legacy (fail at once).
    "webFirstAssertions": {
      "type": "boolean"
    },
    "assertTimeout": {
      "type": "integer"
    },
    // M3: attribute read by testid@ locators (blank = data-testid); strictLocators = a semantic locator
    // matching more than one element fails and lists the candidates.
    "testIdAttribute": {
      "type": "string"
    },
    "strictLocators": {
      "type": "boolean"
    },
    // M5: storage-state file every UI script loads after its browser opens; loginOnce = the script run once first
    // to produce it. browserApiStorageToken = [localStorage:|sessionStorage:]key[#field] sent as a bearer token by
    // api/curl calls from UI scripts.
    "storageState": {
      "type": "string"
    },
    "loginOnce": {
      "type": "string"
    },
    "browserApiStorageToken": {
      "type": "string"
    },
    "seleniumScripts": {
      "type": "array",
      "items": {
        "nolabel": true,
        "type": "string"
      }
    },
    "seleniumLoggerPreferences": {
      "type": "string"
    },
    "javaHome": {
      "type": "string"
    },
    "gatfJarPath": {
      "type": "string"
    },
    // Q8: api/curl calls from a UI script share the browser's cookies (sync = both ways, send = browser → API only).
    "browserApiSession": {
      "label": {
        "type": "section",
        "value": "UI ↔ API session"
      },
      "type": "string",
      "options": ["sync", "send", "off"]
    },
    "seleniumDriverConfigs": {
      "label": {
        "type": "section",
        "value": "Selenium Driver Configurations"
      },
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string"
          },
          "version": {
            "type": "string"
          },
          "platform": {
            "type": "string"
          },
          "driverName": {
            "type": "string"
          },
          "path": {
            "type": "string"
          },
          "arguments": {
            "type": "string"
          },
          "capabilities": {
            "label": {
              "type": "section",
              "value": "Capabilities"
            },
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
          "properties": {
            "label": {
              "type": "section",
              "value": "Properties"
            },
            "type": "map",
            "types": {
              "key": {
                "type": "string"
              },
              "value": {
                "type": "string"
              }
            }
          }
        }
      }
    },
    "debugEnabled": {
      "type": "boolean",
      "required": true
    },
    "isOrderByFileName": {
      "type": "boolean",
      "required": true
    },
    "isServerLogsApiAuthEnabled": {
      "type": "boolean",
      "required": true
    },
    "serverLogsApiAuthExtractAuth": {
      "type": "string"
    },
    "isFetchFailureLogs": {
      "type": "boolean",
      "required": true
    },
    "testCaseHooksPaths": {
      "type": "array",
      "items": {
        "nolabel": true,
        "type": "string"
      }
    },
    "distributedNodes": {
      "label": {
        "type": "section",
        "value": "Distributed Load Test Nodes"
      },
      "type": "array",
      "items": {
        "nolabel": true,
        "type": "string"
      }
    },
    "ignoreFiles": {
      "type": "array",
      "items": {
        "nolabel": true,
        "type": "string"
      }
    },
    "orderedFiles": {
      "type": "array",
      "items": {
        "nolabel": true,
        "type": "string",
        "enumVar": "alltestcasefiles"
      }
    },
    "gatfTestDataConfig": {
      "type": "object",
      "properties": {
        "globalVariables": {
          "label": {
            "type": "section",
            "value": "Global Test Variables"
          },
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
        "compareEnvBaseUrls": {
          "label": {
            "type": "section",
            "value": "Comparison Environments"
          },
          "type": "array",
          "items": {
            "nolabel": true,
            "type": "string"
          }
        },
        "dataSourceList": {
          "label": {
            "type": "section",
            "value": "Data Sources"
          },
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "dataSourceName": {
                "type": "string"
              },
              "dataSourceClass": {
                "type": "string",
                "enumVar": "miscMap.datasourcecls"
              },
              "poolSize": {
                "type": "integer"
              },
              "newResourceCheckoutTimeMs": {
                "type": "number"
              },
              "args": {
                "type": "array",
                "items": {
                  "nolabel": true,
                  "type": "string"
                }
              }
            }
          }
        },
        "dataSourceListForProfiling": {
          "label": {
            "type": "section",
            "value": "Data Sources For Profiling"
          },
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "dataSourceName": {
                "type": "string"
              },
              "dataSourceClass": {
                "type": "string",
                "enumVar": "miscMap.datasourcecls"
              },
              "poolSize": {
                "type": "integer"
              },
              "newResourceCheckoutTimeMs": {
                "type": "number"
              },
              "args": {
                "type": "array",
                "items": {
                  "nolabel": true,
                  "type": "string"
                }
              }
            }
          }
        },
        "providerTestDataList": {
          "label": {
            "type": "section",
            "value": "Test Data Providers"
          },
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "dataSourceName": {
                "type": "string",
                "enumVar": "miscMap.datasources"
              },
              "providerName": {
                "type": "string"
              },
              "providerClass": {
                "type": "string",
                "enumVar": "miscMap.providercls"
              },
              "sourceProperties": {
                "type": "string"
              },
              "providerProperties": {
                "type": "string"
              },
              "queryStr": {
                "type": "string"
              },
              "enabled": {
                "type": "boolean"
              },
              "args": {
                "type": "array",
                "items": {
                  "nolabel": true,
                  "type": "string"
                }
              },
              "live": {
                "type": "boolean"
              }
            }
          }
        },
        "dataSourceHooks": {
          "label": {
            "type": "section",
            "value": "Data Source Hooks"
          },
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "hookName": {
                "type": "string"
              },
              "hookClass": {
                "type": "string"
              },
              "dataSourceName": {
                "type": "string",
                "enumVar": "miscMap.datasources"
              },
              "queryStrs": {
                "type": "array",
                "items": {
                  "nolabel": true,
                  "type": "string"
                }
              },
              "executeOnStart": {
                "type": "boolean",
                "required": true
              },
              "executeOnShutdown": {
                "type": "boolean",
                "required": true
              }
            }
          }
        }
      },
      "name": "GatfTestDataConfig"
    },
    "wrkPath": {
      "type": "string"
    },
    "wrk2Path": {
      "type": "string"
    },
    "vegetaPath": {
      "type": "string"
    },
    "autocannonPath": {
      "type": "string"
    },
    "nodePath": {
      "type": "string"
    },
    "perfToolContainer": {
      "type": "string"
    },
    "extraProperties": {
      "label": {
        "type": "section",
        "value": "Additional Properties"
      },
      "type": "map",
      "types": {
        "key": {
          "type": "string"
        },
        "value": {
          "type": "string"
        }
      }
    }
  }
};

export const generatorSchema: CfgSchema = {
  "type": "object",
  "properties": {
    "testPaths": {
      "type": "array",
      "items": {
        "nolabel": true,
        "type": "string"
      }
    },
    "soapWsdlKeyPairs": {
      "type": "array",
      "items": {
        "nolabel": true,
        "type": "string"
      }
    },
    "urlPrefix": {
      "type": "string"
    },
    "requestDataType": {
      "type": "string"
    },
    "responseDataType": {
      "type": "string"
    },
    "resourcepath": {
      "type": "string"
    },
    "enabled": {
      "type": "boolean",
      "required": true
    },
    "overrideSecure": {
      "type": "boolean",
      "required": true
    },
    "useSoapClient": {
      "type": "boolean",
      "required": true
    },
    "urlSuffix": {
      "type": "string"
    },
    "postmanCollectionVersion": {
      "type": "integer"
    },
    "testCaseFormat": {
      "type": "string"
    }
  }
};

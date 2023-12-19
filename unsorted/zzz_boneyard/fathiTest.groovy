// [
//     {
//      "SheetName":"Administration Flow Page v5",
//      "Program Lookup":"A&I - Boomi (DSP00040)",
//      "Created By":"james.d.bauer@pfizer.com"
//     },
//    {
//      "SheetName":"API Services v1.0",
//      "Program Lookup":"A&I - Boomi (DSP00040)",
//      "Created By":"james.d.bauer@pfizer.com"
//     }
// ]

import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

logger = ExecutionUtil.getBaseLogger();

def OFS = "|^|"  // Output Field Separater

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);


def json = '''
[
  {
    "RowNumber" : 1,
    "cells" : [
      {
        "ColumnName" : "Sheet Name",
        "Value" : "Administration Flow Page v5"
      },
      {
        "ColumnName" : "Program Lookup",
        "Value" : "A&I - Boomi (DSP00040)"
      },
      {
        "ColumnName" : "Created By"
      }
    ]
  },
  {
    "RowNumber" : 2,
    "cells" : [
      {
        "ColumnName" : "Sheet Name",
        "Value" : "API Services v1.0"
      },
      {
        "ColumnName" : "Program Lookup",
        "Value" : "A&I - Boomi (DSP00040)"
      },
      {
        "ColumnName" : "Created By",
        "Value" : "james.d.bauer@pfizer.com"
      }
    ]
  }
]
'''


    def root = new JsonSlurper().parseText(json)
    def newRoot = root.collect { row ->
        row.cells.collectEntries { cell ->
            ["$cell.ColumnName": cell.Value]
        }
    }
    // def newRoot = new JsonSlurper().parseText('{}')

    is = new ByteArrayInputStream(JsonOutput.prettyPrint(JsonOutput.toJson(newRoot)).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

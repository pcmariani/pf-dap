def json = '''
{
  "Records" : [
    {
      "UserInputId" : 30
    },
    {
      "UserInputId" : 31
    },
    {
      "UserInputId" : 32
    },
    {
      "UserInputId" : 33
    }
  ]
}
'''

import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def userInputsArr = new JsonSlurper().parseText(json).Records.UserInputId

    userInputsArr.eachWithIndex { uiid, j ->
        props.setProperty("document.dynamic.userdefined.ddp_userInputId-" + j.toString(), uiid.toString())
    }

    is = new ByteArrayInputStream(JsonOutput.prettyPrint(JsonOutput.toJson(userInputsArr)).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

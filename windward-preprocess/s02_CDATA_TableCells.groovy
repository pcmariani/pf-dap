import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    // regex: 3 capture groups: opening th/td, content, closing th/td
    def data = is.text.replaceAll(
        /(?s)(<t[hd].*?>)\s*(.*?)\s*(<\/t[hd]>)/,
        "\$1<![CDATA[\$2]]>\$3"
    )

    is = new ByteArrayInputStream(data.getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}


package org.apache.struts2.webjars;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.result.StrutsResultSupport;
import org.apache.struts2.webjars.matcher.AntPathMatcher;
import org.apache.struts2.webjars.matcher.PathMatcher;
import org.apache.struts2.webjars.utils.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webjars.WebJarAssetLocator;

import com.opensymphony.xwork2.ActionInvocation;

/**
 * <p>
 * <b>Example:</b>
 * </p>
 *
 * <pre><!-- START SNIPPET: example -->
 * &lt;result name="success" type="webjars"&gt;
 *   &lt;param name="inputName"&gt;inputPattern&lt;/param&gt;
 * &lt;/result&gt;
 * <!-- END SNIPPET: example --></pre>
 *
 */
public class WebjarsStreamResult extends StrutsResultSupport {

    private static final long serialVersionUID = -1468409635999059850L;

    protected static final Logger LOG = LoggerFactory.getLogger(WebjarsStreamResult.class);

    public static final String DEFAULT_PARAM = "inputName";
    
    protected static final String RESOURCE_CHARSET = "UTF-8";

    protected static final String DEFAULT_MINE_TYPE = "application/octet-stream";

    protected final PathMatcher pathMatcher = new AntPathMatcher();
    
  	protected final WebJarAssetLocator assetLocator = new WebJarAssetLocator();
  	
  	protected ConcurrentMap<String, String> COMPLIED_RESOURCE = new ConcurrentHashMap<String, String>();
  	
    private static final Map<String, String> MINE_TYPE_MAP;

    static {
    	
        MINE_TYPE_MAP = new HashMap<String, String>();
        MINE_TYPE_MAP.put("js", "application/javascript;charset=" + RESOURCE_CHARSET);
        MINE_TYPE_MAP.put("css", "text/css;charset=" + RESOURCE_CHARSET);
        MINE_TYPE_MAP.put("gif", "image/gif");
        MINE_TYPE_MAP.put("jpg", "image/jpeg");
        MINE_TYPE_MAP.put("jpeg", "image/jpeg");
        MINE_TYPE_MAP.put("png", "image/png");
        
    }

    protected String contentDisposition = "inline";
    protected String contentCharSet ;
    protected String inputName = "inputPattern";
    protected String inputPattern;
    protected boolean allowCaching = true;
    
    public WebjarsStreamResult() {
        super();
    }

    public WebjarsStreamResult(String pattern) {
        this.inputPattern = pattern;
    }

     /**
     * @return Returns the whether or not the client should be requested to allow caching of the data stream.
     */
    public boolean getAllowCaching() {
        return allowCaching;
    }

    /**
     * Set allowCaching to <tt>false</tt> to indicate that the client should be requested not to cache the data stream.
     * This is set to <tt>false</tt> by default
     *
     * @param allowCaching Enable caching.
     */
    public void setAllowCaching(boolean allowCaching) {
        this.allowCaching = allowCaching;
    }
    
    /**
     * @return Returns the Content-disposition header value.
     */
    public String getContentDisposition() {
        return contentDisposition;
    }

    /**
     * @param contentDisposition the Content-disposition header value to use.
     */
    public void setContentDisposition(String contentDisposition) {
        this.contentDisposition = contentDisposition;
    }

    /**
     * @return Returns the charset specified by the user
     */
    public String getContentCharSet() {
        return contentCharSet;
    }

    /**
     * @param contentCharSet the charset to use on the header when sending the stream
     */
    public void setContentCharSet(String contentCharSet) {
        this.contentCharSet = contentCharSet;
    }

    /**
     * @return Returns the inputName.
     */
    public String getInputName() {
        return (inputName);
    }

    /**
     * @param inputName The inputName to set.
     */
    public void setInputName(String inputName) {
        this.inputName = inputName;
    }
    
    public String getInputPattern() {
		return inputPattern;
	}

	public void setInputPattern(String inputPattern) {
		this.inputPattern = inputPattern;
	}

	/**
     * @see StrutsResultSupport#doExecute(java.lang.String, com.opensymphony.xwork2.ActionInvocation)
     */
    protected void doExecute(String finalLocation, ActionInvocation invocation) throws Exception {
        
        if (inputPattern == null) {
            LOG.debug("Find the inputPattern from the invocation variable stack");
            inputPattern = (String) invocation.getStack().findValue(conditionalParse(inputName, invocation));
        }

        if (inputPattern == null) {
            String msg = ("Can not find a pattern with the name [" + inputName + "] in the invocation stack. " +
                "Check the <param name=\"inputName\"> tag specified for this action.");
            LOG.error(msg);
            throw new IllegalArgumentException(msg);
        }
        
        LOG.debug("Find the Request in context");
        HttpServletRequest oRequest = ServletActionContext.getRequest();
        
        LOG.debug("Find the Response in context");
        HttpServletResponse oResponse = ServletActionContext.getResponse();
        
        //uri去掉web上下文
    	String resPath = oRequest.getRequestURI().substring(oRequest.getContextPath().length());
    	
		//匹配资源路径是否需要处理
    	if(pathMatcher.match(inputPattern, resPath)){
    		InputStream input = null;
    		OutputStream output = null;
    		try {
	    		//资源已经访问过
	        	String ret = COMPLIED_RESOURCE.get(resPath);
	        	String fullPath = ret;
	        	if (ret == null) {
	        		//classpath:/META-INF/resources/webjars/
	        		fullPath = assetLocator.getFullPath(resPath);
	    		} 
	        	//从Jar中获取资源
	        	URL resource = ResourceUtils.getResourceAsURL(fullPath);
        		//如果在类路径中没有找到资源->404
        		if (resource == null) {      
                    oResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
        		} else {
					//获取文件的输入流
					input = ResourceUtils.getURLAsStream(resource);
					if (input != null) { 
						//有input说明已经读到jar中内容
                        String ext = FilenameUtils.getExtension(fullPath).toLowerCase();
                        String contentType = MINE_TYPE_MAP.get(ext);
                        if (contentType == null) {
                            contentType = DEFAULT_MINE_TYPE;
                        }
                        //设置内容类型
                        oResponse.setContentType(contentType);    
						
                        LOG.debug("Set the content type: {};charset{}", contentType, contentCharSet);
                        if (contentCharSet != null && ! contentCharSet.equals("")) {
                            oResponse.setContentType(conditionalParse(contentType, invocation)+";charset="+conditionalParse(contentCharSet, invocation));
                        } else {
                            oResponse.setContentType(conditionalParse(contentType, invocation));
                        }
                        
                    	output = oResponse.getOutputStream();
                    	
                    	LOG.debug("Set the content-disposition: {}", contentDisposition);
                        if (contentDisposition != null) {
                            oResponse.addHeader("Content-Disposition", conditionalParse(contentDisposition, invocation));
                        }

                        LOG.debug("Set the cache control headers if necessary: {}", allowCaching);
                        if (!allowCaching) {
                            oResponse.setHeader("Pragma", "No-cache"); 
                            oResponse.setHeader("Cache-Control", "No-cache"); 
                            oResponse.setDateHeader("Expires", 0);
                        }
                        
                    	//向输出流输出内容
                        int contentLength = IOUtils.copy(input, output);  
                        LOG.debug("Set the content length: {}", contentLength);
                        if (contentLength >= 0) {
                            oResponse.setContentLength(contentLength);
                        }
                        
                        // Flush
                        output.flush();
                            
   	        		} else {   
                    	//没有input->404
                        oResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
                    }
        		}
        		COMPLIED_RESOURCE.putIfAbsent(resPath, fullPath);
            } catch (Exception e) {
            	LOG.error(e.getMessage(), e);
                oResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } finally {
                IOUtils.closeQuietly(input);
                IOUtils.closeQuietly(output);
            }
    	}
    }

}

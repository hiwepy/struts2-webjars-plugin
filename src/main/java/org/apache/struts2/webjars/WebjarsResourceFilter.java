package org.apache.struts2.webjars;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.struts2.webjars.matcher.AntPathMatcher;
import org.apache.struts2.webjars.matcher.PathMatcher;
import org.apache.struts2.webjars.utils.ResourceUtils;
import org.apache.struts2.webjars.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webjars.WebJarAssetLocator;

/**
 * 
 * @className	： WebjarsResourceFilter
 * @description	： TODO(描述这个类的作用)
 * @author 		： <a href="https://github.com/hiwepy">hiwepy</a>
 * @date		： 2017年5月29日 下午4:28:19
 * @version 	V1.0
 */
public class WebjarsResourceFilter implements Filter {

	protected static final String RESOURCE_CHARSET = "UTF-8";

    protected static final String DEFAULT_MINE_TYPE = "application/octet-stream";

    protected final Logger LOG = LoggerFactory.getLogger(this.getClass());

    protected final PathMatcher pathMatcher = new AntPathMatcher();
    
  	protected final WebJarAssetLocator assetLocator = new WebJarAssetLocator();
  	
  	protected ConcurrentMap<String, String> COMPLIED_RESOURCE = new ConcurrentHashMap<String, String>();
  	
  	protected Map<String, String> patternMap = new HashMap<String, String>();

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

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        //初始化资源映射
    	Enumeration<String> elements = filterConfig.getInitParameterNames();
    	while (elements.hasMoreElements()) {
    		String name = elements.nextElement();
    		patternMap.put(name, filterConfig.getInitParameter(name));
    	}
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    	
    	HttpServletRequest oRequest = WebUtils.toHttp(request);
    	HttpServletResponse oResponse = WebUtils.toHttp(response);
    	//定义了资源处理规则
    	if(!patternMap.isEmpty()){
    		//uri去掉web上下文
        	String resPath = oRequest.getRequestURI().substring(oRequest.getContextPath().length());  
    		//对资源路径进行匹配
    		for (String pattern : patternMap.keySet()) {
    			//匹配资源路径是否需要处理
    	    	if(pathMatcher.match(pattern, resPath)){
    	    		InputStream input = null;
    	    		OutputStream output = null;
    	    		String webjar = patternMap.get(pattern);
    	    		try {
	    	    		//资源已经访问过
	    	        	String ret = COMPLIED_RESOURCE.get(resPath);
	    	        	String fullPath = ret;
	    	        	if (ret == null) {
	    	        		fullPath = assetLocator.getFullPath(webjar, resPath);
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
								
								//将文件写出
								if(WebUtils.isGzipInRequest(oRequest)){
									output = new GzipCompressorOutputStream(response.getOutputStream());
								} else {
									output = response.getOutputStream();
								}
                                try {
                                    int size = IOUtils.copy(input, output);  //向输出流输出内容
                                    oResponse.setContentLength(size);
                                } finally {
                                    IOUtils.closeQuietly(input);
                                    IOUtils.closeQuietly(output);
                                }
		   	        		} else {   
	                        	//没有input->404
	                            oResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
	                        }
	   	        		}
	   	        		COMPLIED_RESOURCE.putIfAbsent(resPath, fullPath);
    	            } catch (Exception e) {
    	            	LOG.error(e.getMessage(), e);
                        oResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    	            }
    	    	}
			}
    	}
    	//LOG.error("MUST set url-pattern=\"" + pattern + "/*\"!!");
        //rep.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    
    @Override
    public void destroy() {
    }
    
}

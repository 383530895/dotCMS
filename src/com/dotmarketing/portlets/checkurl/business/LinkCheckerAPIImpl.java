package com.dotmarketing.portlets.checkurl.business;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.Identifier;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.filters.CMSFilter;
import com.dotmarketing.portlets.checkurl.bean.CheckURLBean;
import com.dotmarketing.portlets.checkurl.bean.URL;
import com.dotmarketing.portlets.checkurl.util.ProxyManager;
import com.dotmarketing.util.UtilMethods;

public class LinkCheckerAPIImpl implements LinkCheckerAPI {
    
    public static String ANCHOR = "a";  
    public static String HREF = "href";
    public static String TITLE = "title";
    public static String HTTPS = "https";
    public static String HTTP = "http";
    public static String PARAGRAPH = "#";
    
    @SuppressWarnings("deprecation")
    private static void loadProxy(HttpClient client){
        if(ProxyManager.INSTANCE.isLoaded()){
            if(ProxyManager.INSTANCE.getConnection().isProxy()){
                client.getHostConfiguration().setProxy(ProxyManager.INSTANCE.getConnection().getProxyHost(), ProxyManager.INSTANCE.getConnection().getProxyPort());
                if(ProxyManager.INSTANCE.getConnection().isProxyRequiredAuth()){
                    HttpState state = new HttpState();
                    state.setProxyCredentials(null, null,
                            new UsernamePasswordCredentials(ProxyManager.INSTANCE.getConnection().getProxyUsername(), ProxyManager.INSTANCE.getConnection().getProxyPassword()));
                    client.setState(state);
                }
            }
        }       
    }
    
    public static URL getURLByString(String href){
        try {
            java.net.URL url = new java.net.URL(href);
            URL urlBean = new URL();
            if(url.getProtocol().equals(HTTPS))
                urlBean.setHttps(true);
            else
                urlBean.setHttps(false);
            urlBean.setHostname(url.getHost());         
            urlBean.setPort(url.getPort()<0?80:url.getPort());
            urlBean.setPath(url.getPath());
            if(url.getQuery()!=null){
                urlBean.setWithParameter(true);
                String[] query_string = null;
                if(url.getQuery().split("[&amp;]").length>0)
                    query_string = url.getQuery().split("[&amp;]");
                else
                    query_string = url.getQuery().split("[&]");
                NameValuePair[] params = new NameValuePair[query_string.length];
                for(int i=0; i<query_string.length; i++){
                    String[] parametro_arr = query_string[i].split("[=]");
                    params[i] = new NameValuePair(parametro_arr[0], parametro_arr[1]);
                }
                urlBean.setQueryString(params);             
            }
            return urlBean;
        } catch (MalformedURLException e) {
            return null;
        }
    }
    
    @Override
    public List<CheckURLBean> findInvalidLinks(String htmltext) throws DotDataException, DotSecurityException {
        List<Anchor> anchorList = new ArrayList<Anchor>();
        Document doc = Jsoup.parse(htmltext);
        Elements links = doc.select(ANCHOR);
        for(Element link:links){
            String href = link.attr(HREF);
            Anchor a = new Anchor();
            if(href.startsWith(HTTP) || href.startsWith(HTTPS)){ //external link                
                a.setExternalLink(getURLByString(href));
                a.setTitle(link.attr(TITLE));
                a.setInternalLink(null);
                a.setInternal(false);   
                anchorList.add(a);
            }else if(!(href.startsWith(PARAGRAPH))){ //internal link                
                a.setExternalLink(null);
                a.setTitle(link.attr(TITLE));
                if(href.indexOf('?')>0)
                    a.setInternalLink(href.substring(0,href.indexOf('?')));
                else
                    a.setInternalLink(href);
                a.setInternal(true);
                anchorList.add(a);
            }
            
        }
        List<Host> hosts=APILocator.getHostAPI().findAll(APILocator.getUserAPI().getSystemUser(), false);
        List<CheckURLBean> result = new ArrayList<CheckURLBean>();
        for(Anchor a : anchorList){
            if(a.getExternalLink()!=null && (!a.isInternal())) { //external link
                HttpClient client = new HttpClient();
                loadProxy(client);
                HttpMethod method = new GetMethod(a.getExternalLink().absoluteURL());
                if(a.getExternalLink().isWithParameter())
                    method.setQueryString(a.getExternalLink().getQueryString());
                int statusCode = -1;
                try{
                    statusCode = client.executeMethod(method);
                } catch(Exception e){ }
                
                if(statusCode!=200){
                    CheckURLBean c = new CheckURLBean();
                    c.setUrl(a.getExternalLink().absoluteURL());
                    c.setStatusCode(statusCode);
                    c.setTitle(a.getTitle());
                    c.setInternalLink(false);
                    result.add(c);
                }
            }else {  //internal link.
                boolean found=false;
                if(!CMSFilter.excludeURI(a.getInternalLink())) {
                    for(Host h : hosts){
                        Identifier id = APILocator.getIdentifierAPI().find(h, a.getInternalLink());
                        if(id!=null && UtilMethods.isSet(id.getId())) {
                            found = true; break;
                        }
                    }
                    if(!found) {
                        CheckURLBean c = new CheckURLBean();
                        c.setUrl(a.getInternalLink());
                        c.setTitle(a.getTitle());
                        c.setInternalLink(true);
                        result.add(c);
                    }
                }
            }
        }
        return result;
    }
    
    protected static class Anchor {
        
        private URL externalLink;
        private String title;
        private String internalLink;
        private boolean isInternal;
        
        public URL getExternalLink() {
            return externalLink;
        }
        
        public void setExternalLink(URL href) {
            this.externalLink = href;
        }
        
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }

        public String getInternalLink() {
            return internalLink;
        }

        public void setInternalLink(String internalLink) {
            this.internalLink = internalLink;
        }

        public boolean isInternal() {
            return isInternal;
        }

        public void setInternal(boolean isInternal) {
            this.isInternal = isInternal;
        }
        
    }
    
}

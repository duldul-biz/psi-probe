/*
 * Licensed under the GPL License. You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * THIS PACKAGE IS PROVIDED "AS IS" AND WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF MERCHANTIBILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE.
 */

package com.googlecode.psiprobe.controllers.jsp;

import com.googlecode.psiprobe.Utils;
import com.googlecode.psiprobe.controllers.ContextHandlerController;
import com.googlecode.psiprobe.model.jsp.Item;
import com.googlecode.psiprobe.model.jsp.Summary;

import org.apache.catalina.Context;
import org.apache.jasper.EmbeddedServletOptions;
import org.apache.jasper.Options;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.servlet.ModelAndView;

import java.io.InputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 
 * @author Vlad Ilyushchenko
 */
public class ViewSourceController extends ContextHandlerController {

  protected ModelAndView handleContext(String contextName, Context context,
      HttpServletRequest request, HttpServletResponse response) throws Exception {

    String jspName = ServletRequestUtils.getStringParameter(request, "source", null);
    boolean highlight = ServletRequestUtils.getBooleanParameter(request, "highlight", true);
    Summary summary =
        (Summary) (request.getSession() != null ? request.getSession().getAttribute(
            DisplayJspController.SUMMARY_ATTRIBUTE) : null);

    if (jspName != null && summary != null && contextName.equals(summary.getName())) {

      Item item = (Item) summary.getItems().get(jspName);

      if (item != null) {
        // replace "\" with "/"
        jspName = jspName.replaceAll("\\\\", "/");

        // remove cheeky "../" from the path to avoid exploits
        while (jspName.indexOf("../") != -1) {
          jspName = jspName.replaceAll("\\.\\./", "");
        }

        if (getContainerWrapper().getTomcatContainer().resourceExists(jspName, context)) {
          ServletContext sctx = context.getServletContext();
          ServletConfig scfg = (ServletConfig) context.findChild("jsp");
          Options opt = new EmbeddedServletOptions(scfg, sctx);
          String descriptorPageEncoding =
              opt.getJspConfig().findJspProperty(jspName).getPageEncoding();

          if (descriptorPageEncoding != null && descriptorPageEncoding.length() > 0) {
            item.setEncoding(descriptorPageEncoding);
          } else {

            /*
             * we have to read the JSP twice, once to figure out the content encoding the second
             * time to read the actual content using the correct encoding
             */
            InputStream encodedStream =
                getContainerWrapper().getTomcatContainer().getResourceStream(jspName, context);
            item.setEncoding(Utils.getJSPEncoding(encodedStream));
          }
          InputStream jspStream =
              getContainerWrapper().getTomcatContainer().getResourceStream(jspName, context);
          if (highlight) {
            request.setAttribute("highlightedContent",
                Utils.highlightStream(jspName, jspStream, "xhtml", item.getEncoding()));
          } else {
            request.setAttribute("content", Utils.readStream(jspStream, item.getEncoding()));
          }

        } else {
          logger.error(jspName + " does not exist");
        }

        request.setAttribute("item", item);

      } else {
        logger.error("jsp name passed is not in the summary, ignored");
      }
    } else {
      if (jspName == null) {
        logger.error("Passed empty \"source\" parameter");
      }
      if (summary == null) {
        logger.error("Session has expired or there is no summary");
      }
    }
    return new ModelAndView(getViewName());
  }

}
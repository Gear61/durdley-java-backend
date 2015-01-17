import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.*;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.*;

public class Main extends HttpServlet
{
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
			IOException
	{
		showHome(req, resp);
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
	{
		// Read in request body (which should be a JSON)
		StringBuffer jb = new StringBuffer();
		String line = null;
		try
		{
			BufferedReader reader = req.getReader();
			while ((line = reader.readLine()) != null)
			{
				jb.append(line);
			}
		}
		catch (Exception e)
		{
		}
		resp.getWriter().print(jb.toString());
	}

	private void showHome(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException
	{
		resp.getWriter().print("Durdle");
	}

	public static void main(String[] args) throws Exception
	{
		Server server = new Server(Integer.valueOf(System.getenv("PORT")));
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		server.setHandler(context);
		context.addServlet(new ServletHolder(new Main()), "/*");
		server.start();
		server.join();
	}
}

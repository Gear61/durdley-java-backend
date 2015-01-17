import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.*;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
// Alex's stuff
import java.io.BufferedReader;
import java.lang.StringBuffer;

import org.json.JSONException;
import org.json.JSONObject;

@SuppressWarnings("serial")
public class Main extends HttpServlet
{
	private static final String TABLE_CREATION = "CREATE TABLE Persons (targetPhoneNumber int, description varchar(255), describerPhoneNumber int)";
  
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
	{
		// Create table if it doesn't exist
		Connection connection = null;
		try
		{
			connection = getConnection();
			Statement stmt = connection.createStatement();
			stmt.executeUpdate(TABLE_CREATION);
		}
		catch (Exception e)
		{
			resp.getWriter().print("There was an error: " + e.getMessage());
			return;
		}
		finally
		{
		  if (connection != null)
		  {
			  try
			  {
				  connection.close();
			  }
			  catch(SQLException e){}
		  }
		}
		
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
		catch (Exception e){}

		// Parse the request body into a JSON object
		try
		{
			JSONObject jsonObject = new JSONObject (jb.toString());
			if (req.getRequestURI().endsWith("/addDescription"))
			{
				addDescriptions(req, resp, jsonObject);
			}
		}
		catch (JSONException e)
		{
			// crash and burn
			throw new IOException("Error parsing JSON request string");
		}
	}

	private void addDescriptions(HttpServletRequest req, HttpServletResponse resp, JSONObject requestBody)
		throws ServletException, IOException
	{
		resp.getWriter().print("We are adding descriptions!");
	}
	

	private Connection getConnection() throws URISyntaxException, SQLException
	{
		URI dbUri = new URI(System.getenv("DATABASE_URL"));

		String username = dbUri.getUserInfo().split(":")[0];
		String password = dbUri.getUserInfo().split(":")[1];
		int port = dbUri.getPort();

		String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + ":" + port + dbUri.getPath();

		return DriverManager.getConnection(dbUrl, username, password);
	}

	public static void main(String[] args) throws Exception
	{
		Server server = new Server(Integer.valueOf(System.getenv("PORT")));
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		server.setHandler(context);
		context.addServlet(new ServletHolder(new Main()),"/*");
		server.start();
		server.join();
	}
}

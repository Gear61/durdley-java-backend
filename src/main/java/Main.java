import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.*;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Main extends HttpServlet
{
	private static final String TABLE_CREATION = "CREATE TABLE Descriptions IF NOT EXISTS (targetPhoneNumber varchar(15), description varchar(255), describerPhoneNumber varchar(15), "
											   + "CONSTRAINT uniqueDescription UNIQUE (targetPhoneNumber, description, describerPhoneNumber))";
	
	// JSON keys
	String DESCRIBER_PHONE_NUMBER_KEY = "describerPhoneNumber";
	String TARGET_PHONE_NUMBER_KEY = "targetPhoneNumber";
	String DESCRIPTIONS_KEY = "descriptions";
	
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
				catch (SQLException e) {}
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
		catch (IOException e) {}
		
		// Parse the request body into a JSON object
		try
		{
			JSONObject jsonObject = new JSONObject(jb.toString());
			if (req.getRequestURI().endsWith("/addDescriptions"))
			{
				addDescriptions(resp, jsonObject);
			}
			
			if (req.getRequestURI().endsWith("/removeDescription"))
			{
				addDescriptions(resp, jsonObject);
			}
			
			if (req.getRequestURI().endsWith("/getDescriptionsOfXByY"))
			{
				addDescriptions(resp, jsonObject);
			}
			
			if (req.getRequestURI().endsWith("/descriptionProfile"))
			{
				addDescriptions(resp, jsonObject);
			}
		}
		catch (JSONException e1) {}
	}
	
	// 1. /addDesciptions
	// Add some amount of descriptions of target person by a certain describer
	/* EXAMPLE REQUEST
	{
	   "describerPhoneNumber":"5104494353",
	   "targetPhoneNumber":"5103660115",
	   "descriptions":["smart", "a durdle"]
	} */
	private void addDescriptions(HttpServletResponse resp, JSONObject requestBody)
		throws ServletException, IOException, JSONException
	{
		JSONObject response = new JSONObject();
		try
		{
			String describerPhoneNumber = requestBody.getString(DESCRIBER_PHONE_NUMBER_KEY);
			String targetPhoneNumber = requestBody.getString(TARGET_PHONE_NUMBER_KEY);
			JSONArray descriptions = requestBody.getJSONArray(DESCRIPTIONS_KEY);

			resp.getWriter().print("Describer phone number: " + describerPhoneNumber + "\n");
			resp.getWriter().print("Target phone number: " + targetPhoneNumber + "\n");
			resp.getWriter().print("Descriptions: ");
			
			for (int i = 0; i < descriptions.length(); i++)
			{
				resp.getWriter().print(descriptions.getJSONObject(i).toString());
			}
		}
		catch (Exception e)
		{
			response.put("STATUS", "FAILED");
			resp.getWriter().print(response.toString());
		}
	}
	
	private Connection getConnection() throws URISyntaxException, SQLException
	{
	    URI dbUri = new URI(System.getenv("DATABASE_URL"));

	    String username = dbUri.getUserInfo().split(":")[0];
	    String password = dbUri.getUserInfo().split(":")[1];
	    String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + dbUri.getPath();

	    return DriverManager.getConnection(dbUrl, username, password);
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

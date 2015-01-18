import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.*;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Main extends HttpServlet
{
	private static final String TABLE_CREATION = "CREATE TABLE IF NOT EXISTS Descriptions (targetPhoneNumber varchar(15), description varchar(255), describerPhoneNumber varchar(15), "
											   + "CONSTRAINT uniqueDescription UNIQUE (targetPhoneNumber, description, describerPhoneNumber))";
	
	// JSON keys
	String DESCRIBER_PHONE_NUMBER_KEY = "describerPhoneNumber";
	String TARGET_PHONE_NUMBER_KEY = "targetPhoneNumber";
	String DESCRIPTIONS_KEY = "descriptions";
	
	// JSON response keys
	String DESCRIPTION_KEY = "description";
	String NUM_OCCURENCES_KEY = "num_occurences";
	
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
				addDescriptions(resp, jsonObject, connection);
			}
			
			if (req.getRequestURI().endsWith("/removeDescription"))
			{
				removeDescription(resp, jsonObject, connection);
			}
			
			if (req.getRequestURI().endsWith("/getDescriptionsOfXByY"))
			{
				getDescriptionsOfXByY(resp, jsonObject, connection);
			}
			
			if (req.getRequestURI().endsWith("/descriptionProfile"))
			{
				descriptionProfile(resp, jsonObject, connection);
			}
		}
		catch (JSONException e1) {}
		catch (SQLException e) {}
		finally
		{
			try
			{
				connection.close();
			}
			catch (SQLException e) {}
		}
	}
	
	// 1. /addDesciptions
	// Add some amount of descriptions of target person by a certain describer
	/* EXAMPLE REQUEST
	{
	   "describerPhoneNumber":"5104494353",
	   "targetPhoneNumber":"5103660115",
	   "descriptions":["smart", "a durdle"]
	} */
	private void addDescriptions(HttpServletResponse resp, JSONObject requestBody, Connection connection)
		throws ServletException, IOException, JSONException
	{
		JSONObject response = new JSONObject();
		try
		{
			String describerPhoneNumber = requestBody.getString(DESCRIBER_PHONE_NUMBER_KEY);
			String targetPhoneNumber = requestBody.getString(TARGET_PHONE_NUMBER_KEY);
			JSONArray descriptions = requestBody.getJSONArray(DESCRIPTIONS_KEY);

			try
			{
				Statement stmt = connection.createStatement();

				String insertPart1 = "INSERT INTO Descriptions VALUES ('" + targetPhoneNumber + "', '";
				String insertPart2 = "', '" + describerPhoneNumber + "')";
				for (int i = 0; i < descriptions.length(); i++)
				{
					String checkDescription = "SELECT * FROM Descriptions WHERE targetPhoneNumber = '" + targetPhoneNumber + "'" +
							  " AND describerPhoneNumber = '" + describerPhoneNumber +  "'" + " AND description = '" + descriptions.getString(i) + "'";
					ResultSet rs = stmt.executeQuery(checkDescription);
					// If this description has been done before, bail
					if (rs.next())
					{
						continue;
					}
					stmt.executeUpdate(insertPart1 + descriptions.getString(i) + insertPart2);
				}
				response.put("STATUS", "SUCCESS");
			}
			catch (Exception e)
			{
				response.put("STATUS", "FAILED");
				response.put("ERROR", e.getMessage());
			}
		}
		catch (JSONException e)
		{
			response.put("STATUS", "FAILED");
			response.put("ERROR", e.getMessage());
		}
		resp.getWriter().println(response.toString());
	}
	
	// 2. /removeDescription
	// Add some amount of descriptions of target person by a certain describer
	/* EXAMPLE REQUEST
	{
		"describerPhoneNumber":"5104494353",
		"targetPhoneNumber":"5103660115",
		"description":"smart"
	} */
	private void removeDescription(HttpServletResponse resp, JSONObject requestBody, Connection connection)
			throws ServletException, IOException, JSONException, SQLException
	{
		JSONObject response = new JSONObject();
		try
		{
			String describerPhoneNumber = requestBody.getString(DESCRIBER_PHONE_NUMBER_KEY);
			String targetPhoneNumber = requestBody.getString(TARGET_PHONE_NUMBER_KEY);
			String description = requestBody.getString(DESCRIPTION_KEY);

			try
			{
				Statement stmt = connection.createStatement();
				String removeDescription = "DELETE FROM Descriptions WHERE targetPhoneNumber = '" + targetPhoneNumber + "'" +
						  " AND describerPhoneNumber = '" + describerPhoneNumber +  "'" + " AND description = '" + description + "'";
				stmt.executeUpdate(removeDescription);
				response.put("STATUS", "SUCCESS");
			}
			catch (Exception e)
			{
				response.put("STATUS", "FAILED");
				response.put("ERROR", e.getMessage());
			}
		}
		catch (JSONException e)
		{
			response.put("STATUS", "FAILED");
			response.put("ERROR", e.getMessage());
		}
		resp.getWriter().println(response.toString());
	}
	
	// Get how a certain describer described a certain target - Used for editing how you've described someone
	// 3. /getDescriptionsOfXByY
	/* EXAMPLE REQUEST
	{
	   "describerPhoneNumber":"5104494353",
	   "targetPhoneNumber":"5103660115"
	} */
	private void getDescriptionsOfXByY(HttpServletResponse resp, JSONObject requestBody, Connection connection)
			throws ServletException, IOException, JSONException
	{
		JSONObject response = new JSONObject();
		JSONArray descriptions = new JSONArray();
		try
		{
			String describerPhoneNumber = requestBody.getString(DESCRIBER_PHONE_NUMBER_KEY);
			String targetPhoneNumber = requestBody.getString(TARGET_PHONE_NUMBER_KEY);
			try
			{
				Statement stmt = connection.createStatement();
				String getDescriptionsQuery = "SELECT description FROM Descriptions WHERE targetPhoneNumber = '" + targetPhoneNumber + "'" +
											  " AND describerPhoneNumber = '" + describerPhoneNumber +  "'";
				ResultSet rs = stmt.executeQuery(getDescriptionsQuery);
				
				// Get all descriptions associated with phone number from DB. Populate hashmap mapping description to frequency
			    while (rs.next())
			    {
			    	String description = rs.getString("description");
			    	descriptions.put(description);
			    }
			    
			    response.put(DESCRIPTIONS_KEY, descriptions);
			}
			catch (Exception e)
			{
				response.put("STATUS", "FAILED");
				response.put("ERROR", e.getMessage());
			}
		}
		catch (JSONException e)
		{
			response.put("STATUS", "FAILED");
			response.put("ERROR", e.getMessage());
		}
		resp.getWriter().print(response.toString());
	}
	
	// Get ALL descriptions that have been assigned to a certain target
	// 4. /descriptionProfile
	/* EXAMPLE REQUEST
	{
		"targetPhoneNumber":"5103660115",
	} */
	private void descriptionProfile(HttpServletResponse resp, JSONObject requestBody, Connection connection)
			throws ServletException, IOException, JSONException, SQLException
	{
		JSONObject response = new JSONObject();
		JSONArray descriptions = new JSONArray();
		try
		{
			String targetPhoneNumber = requestBody.getString(TARGET_PHONE_NUMBER_KEY);
			try
			{
				Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT description FROM Descriptions WHERE targetPhoneNumber = '" + targetPhoneNumber + "'");
				HashMap<String, Integer> descriptionOccurences = new HashMap<String, Integer>();
				
				// Get all descriptions associated with phone number from DB. Populate hashmap mapping description to frequency
			    while (rs.next())
			    {
			    	String description = rs.getString("description");
			    	Integer numOccurences = descriptionOccurences.get(description);
			    	if (numOccurences == null)
			    	{
			    		descriptionOccurences.put(description, 1);
			    	}
			    	else
			    	{
			    		descriptionOccurences.put(description, numOccurences + 1);
			    	}
			    }
			    
			    // Shove hashmap from before into a JSON array
			    for (String currentDescription : descriptionOccurences.keySet())
			    {
			    	JSONObject descriptionPair = new JSONObject();
			    	int numOccurences = descriptionOccurences.get(currentDescription);
			    	descriptionPair.put(DESCRIPTION_KEY, currentDescription);
			    	descriptionPair.put(NUM_OCCURENCES_KEY, numOccurences);
			    	descriptions.put(descriptionPair);
			    }
			    
			    response.put(DESCRIPTIONS_KEY, descriptions);
			}
			catch (Exception e)
			{
				response.put("STATUS", "FAILED");
				response.put("ERROR", e.getMessage());
			}
		}
		catch (JSONException e)
		{
			response.put("STATUS", "FAILED");
			response.put("ERROR", e.getMessage());
		}
		resp.getWriter().print(response.toString());
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

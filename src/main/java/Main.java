import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.*;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Main extends HttpServlet
{
	private static final String TABLE_CREATION = "CREATE TABLE IF NOT EXISTS Descriptions (targetFacebookId varchar(50), description varchar(255), describerFacebookId varchar(50), timestamp(int), "
											   + "CONSTRAINT uniqueDescription UNIQUE (targetFacebookId, description, describerFacebookId))";
	
	// JSON keys
	String DESCRIBER_FACEBOOK_ID_KEY = "describerFacebookId";
	String TARGET_FACEBOOK_ID_KEY = "targetFacebookId";
	String DESCRIPTIONS_KEY = "descriptions";
	
	// JSON response keys
	String DESCRIPTION_KEY = "description";
	String NUM_OCCURENCES_KEY = "num_occurences";
	String DESCRIBERS_KEY = "describers";
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
			IOException
	{
		showHome(req, resp);
	}
	
	// HELPER DURDLES
	private int getCurrentUnixTime()
	{
		return (int) (System.currentTimeMillis() / 1000L);
	}
	
	// Removes a certain description assigned by a certain describer to a certain target
	private void addDescription(Connection connection, String describerFacebookId,
			String targetFacebookId, String description) throws ServletException, IOException,
			JSONException, SQLException
	{
		try
		{
			Statement stmt = connection.createStatement();
			String insertPart1 = "INSERT INTO Descriptions VALUES ('" + targetFacebookId + "', '";
			String insertPart2 = "', '" + describerFacebookId + "', " + String.valueOf(getCurrentUnixTime()) + ")";
			stmt.executeUpdate(insertPart1 + description + insertPart2);
		}
		catch (Exception e) {}
	}
	
	// Removes a certain description assigned by a certain describer to a certain target
	private void removeDescription(Connection connection, String describerFacebookId, String targetFacebookId, String description)
			throws ServletException, IOException, JSONException, SQLException
	{
		try
		{
			Statement stmt = connection.createStatement();
			String removeDescription = "DELETE FROM Descriptions WHERE targetFacebookId = '" + targetFacebookId + "'" +
					  " AND describerFacebookId = '" + describerFacebookId +  "'" + " AND description = '" + description + "'";
			stmt.executeUpdate(removeDescription);
		}
		catch (Exception e) {}
	}
	
	private HashSet<String> getDescriptionsOfXByYQuery (Connection connection, String describerFacebookId, String targetFacebookId)
	{
		HashSet<String> descriptions = new HashSet<String>();
		Statement stmt;
		try
		{
			stmt = connection.createStatement();
			String getDescriptionsQuery = "SELECT description FROM Descriptions WHERE targetFacebookId = '" + targetFacebookId + "'" +
					  " AND describerFacebookId = '" + describerFacebookId +  "'";
			ResultSet rs = stmt.executeQuery(getDescriptionsQuery);

			while (rs.next())
			{
				String description = rs.getString("description");
				descriptions.add(description);
			}
		}
		catch (SQLException e)
		{
		}
		return descriptions;
	}
	
	private void dropTable(HttpServletResponse resp, Connection connection)
			throws ServletException, IOException, JSONException
		{
			JSONObject response = new JSONObject();
			try
			{
				Statement stmt = connection.createStatement();
				stmt.executeUpdate("DROP TABLE Descriptions");
				response.put("STATUS", "SUCCESS");
			}
			catch (JSONException e)
			{
				response.put("STATUS", "FAILED");
				response.put("ERROR", e.getMessage());
			}
			catch (SQLException e)
			{
				response.put("STATUS", "FAILED");
				response.put("ERROR", e.getMessage());
			}
			resp.getWriter().println(response.toString());
		}
	
	// POST routing
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
			
			if (req.getRequestURI().endsWith("/dropTable"))
			{
				dropTable(resp, connection);
			}
			
			if (req.getRequestURI().endsWith("/syncDescriptions"))
			{
				syncDescriptions(resp, jsonObject, connection);
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
	
	// ENDPOINTS
	// Get how a certain describer described a certain target - Used for editing how you've
	// described someone
	// 1. /getDescriptionsOfXByY
	/*
	 * EXAMPLE REQUEST { "describerFacebookId":"5104494353", "targetFacebookId":"5103660115" }
	 */
	private void getDescriptionsOfXByY(HttpServletResponse resp, JSONObject requestBody,
			Connection connection) throws ServletException, IOException, JSONException
	{
		JSONObject response = new JSONObject();
		JSONArray descriptions = new JSONArray();
		try
		{
			String describerFacebookId = requestBody.getString(DESCRIBER_FACEBOOK_ID_KEY);
			String targetFacebookId = requestBody.getString(TARGET_FACEBOOK_ID_KEY);
			try
			{
				HashSet<String> descriptionList = getDescriptionsOfXByYQuery(connection,
						describerFacebookId, targetFacebookId);
				for (String description : descriptionList)
				{
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
	
	// 2. /syncDescriptions
	// Syncs a Describer/Target relationship to what's in the request body, removing/adding descriptions as needed
	/* EXAMPLE REQUEST
	{
	   "describerFacebookId":"5104494353",
	   "targetFacebookId":"5103660115",
	   "descriptions":["smart", "a durdle"]
	} */
	private void syncDescriptions(HttpServletResponse resp, JSONObject requestBody, Connection connection)
		throws ServletException, IOException, JSONException
	{
		JSONObject response = new JSONObject();
		try
		{
			String describerFacebookId = requestBody.getString(DESCRIBER_FACEBOOK_ID_KEY);
			String targetFacebookId = requestBody.getString(TARGET_FACEBOOK_ID_KEY);
			JSONArray descriptions = requestBody.getJSONArray(DESCRIPTIONS_KEY);

			try
			{
				HashSet<String> newDescriptions = new HashSet<String>();
				for (int i = 0; i < descriptions.length(); i++)
				{
					newDescriptions.add(descriptions.getString(i));
				}
				HashSet<String> currentDescriptions = getDescriptionsOfXByYQuery(connection, describerFacebookId, targetFacebookId);
				
				// First, we delete all of the "revoked" descriptions
				for (String currentDescription : currentDescriptions)
				{
					if (!newDescriptions.contains(currentDescription))
					{
						removeDescription(connection, describerFacebookId, targetFacebookId, currentDescription);
					}
				}
				
				// Now we add all of the new descriptions
				for (String newDescription : newDescriptions)
				{
					if (!currentDescriptions.contains(newDescription))
					{
						addDescription(connection, describerFacebookId, targetFacebookId, newDescription);
					}
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
	
	// Get ALL descriptions that have been assigned to a certain target
	// 3. /descriptionProfile
	/* EXAMPLE REQUEST
	{
		"targetFacebookId":"5103660115",
	} */
	private void descriptionProfile(HttpServletResponse resp, JSONObject requestBody, Connection connection)
			throws ServletException, IOException, JSONException, SQLException
	{
		JSONObject response = new JSONObject();
		JSONArray descriptions = new JSONArray();
		try
		{
			String targetFacebookId = requestBody.getString(TARGET_FACEBOOK_ID_KEY);
			try
			{
				Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT description FROM Descriptions WHERE targetFacebookId = '" + targetFacebookId + "'");
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

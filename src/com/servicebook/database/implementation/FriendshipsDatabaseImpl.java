/**
 * 
 */

package com.servicebook.database.implementation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.tomcat.dbcp.dbcp.BasicDataSource;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.mysql.jdbc.MysqlErrorNumbers;
import com.servicebook.database.AbstractMySqlDatabase;
import com.servicebook.database.FriendshipsDatabase;
import com.servicebook.database.exceptions.DatabaseUnkownFailureException;
import com.servicebook.database.exceptions.friendships.ElementAlreadyExistsException;
import com.servicebook.database.exceptions.friendships.InvalidParamsException;
import com.servicebook.database.exceptions.friendships.ReflexiveFriendshipException;
import com.servicebook.database.exceptions.friendships.TableCreationException;
import com.servicebook.database.primitives.DBUser;

/**
 * @author Shmulik
 *
 */
public class FriendshipsDatabaseImpl extends AbstractMySqlDatabase implements
		FriendshipsDatabase {

	@Override
	public boolean areFriends(String username1, String username2)
			throws InvalidParamsException, DatabaseUnkownFailureException {
		boolean $ = false;

		try (Connection conn = getConnection()) {
			conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);

			$ = areFriends(username1, username2, conn);
		} catch (SQLException e) {
			throw new DatabaseUnkownFailureException(e);
		}

		return $;
	}

	@Override
	public boolean areFriends(String username1, String username2,
			Connection conn) throws InvalidParamsException,
			DatabaseUnkownFailureException {
		if (username1 == null || username2 == null || isConnClosed(conn)) {
			throw new InvalidParamsException();
		}
		boolean $ = false;
		try (PreparedStatement stmt = conn.prepareStatement(areFriendsQuery)) {
			stmt.setString(1, username1);
			stmt.setString(2, username2);
			ResultSet rs = stmt.executeQuery();

			if (rs.next()) {
				$ = rs.getBoolean(1);
			}
		} catch (SQLException e) {
			throw new DatabaseUnkownFailureException(e);
		}
		return $;
	}

	@Override
	public void deleteFriendships(String username, Connection conn)
			throws InvalidParamsException, DatabaseUnkownFailureException {
		if (username == null || isConnClosed(conn)) {
			throw new InvalidParamsException();
		}
		try (PreparedStatement stmt = conn
				.prepareStatement(deleteUserFriendsQuery)) {
			stmt.setString(1, username);
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new DatabaseUnkownFailureException(e);
		}
	}

	private enum FriendshipsColumns {
		FIRST_USERNAME, SECOND_USERNAME
	}

	private enum UsersColumns {
		/**
		 * Username column representation
		 */
		USERNAME,
		/**
		 * Password column representation
		 */
		PASSWORD,
		/**
		 * Name column representation
		 */
		NAME,
		/**
		 * Balance column representation
		 */
		BALANCE
	}

	/**
	 * @param friendshipsTable
	 *            The table to be created
	 * @param usersTable
	 *            The users table
	 * @param schema
	 *            the name of the schema
	 * @param datasource
	 *            the connection handler
	 * @throws TableCreationException
	 *             exception thrown in case of failure in creating the database
	 */
	public FriendshipsDatabaseImpl(String friendshipsTable, String usersTable,
			String schema, BasicDataSource datasource)
			throws TableCreationException {
		super(schema, datasource);
		this.friendshipsTable = schema + "." + "`" + friendshipsTable + "`";
		this.usersTable = schema + "." + "`" + usersTable + "`";
		initQueries();

		try (Connection conn = getConnection();
				Statement stmt = conn.createStatement()) {
			stmt.execute(creationQuery);
			conn.commit();
		} catch (final SQLException e) {
			if (e.getErrorCode() != 1061) {
				throw new TableCreationException(e);
			}
		}
	}

	/*
	 * (non-Javadoc) @see
	 * com.servicebook.database.FriendshipsDatabase#addFriendship
	 * (java.lang.String, java.lang.String)
	 */
	@Override
	public void addFriendship(String username1, String username2,
			Connection conn) throws InvalidParamsException,
			ReflexiveFriendshipException, ElementAlreadyExistsException,
			DatabaseUnkownFailureException {
		if (username1 == null || username2 == null) {
			throw new InvalidParamsException();
		}

		if (username1.equalsIgnoreCase(username2)) {
			throw new ReflexiveFriendshipException();
		}

		insertByUsername(username1, username2, conn);
	}

	/*
	 * (non-Javadoc) @see
	 * com.servicebook.database.FriendshipsDatabase#addFriendship
	 * (com.servicebook.database.primitives.DBUser,
	 * com.servicebook.database.primitives.DBUser)
	 */
	@Override
	public void addFriendship(DBUser user1, DBUser user2, Connection conn)
			throws ElementAlreadyExistsException,
			DatabaseUnkownFailureException, InvalidParamsException,
			ReflexiveFriendshipException {
		if (isUserNull(user1) || isUserNull(user2)) {
			throw new InvalidParamsException();
		}
		addFriendship(user1.getUsername(), user2.getUsername(), conn);
	}

	/*
	 * (non-Javadoc) @see
	 * com.servicebook.database.FriendshipsDatabase#getFriends(java.lang.String)
	 */
	@Override
	public List<DBUser> getFriends(String username)
			throws InvalidParamsException, DatabaseUnkownFailureException {
		if (username == null) {
			throw new InvalidParamsException();
		}
		return getByUsername(username);
	}

	/*
	 * (non-Javadoc) @see
	 * com.servicebook.database.FriendshipsDatabase#getFriends
	 * (com.servicebook.database.primitives.DBUser)
	 */
	@Override
	public List<DBUser> getFriends(DBUser user) throws InvalidParamsException,
			DatabaseUnkownFailureException {
		if (isUserNull(user)) {
			throw new InvalidParamsException();
		}
		return getFriends(user.getUsername());
	}

	private void initQueries() {
		creationQuery = String
				.format("CREATE TABLE IF NOT EXISTS %s (`%s` VARCHAR(255) NOT NULL, `%s` VARCHAR(255) NOT NULL, PRIMARY KEY(`%s`, `%s`))",
						friendshipsTable, FriendshipsColumns.FIRST_USERNAME
								.toString().toLowerCase(),
						FriendshipsColumns.SECOND_USERNAME.toString()
								.toLowerCase(),
						FriendshipsColumns.FIRST_USERNAME.toString()
								.toLowerCase(),
						FriendshipsColumns.SECOND_USERNAME.toString()
								.toLowerCase());

		insertionQuery = String.format("INSERT INTO %s (%s, %s) VALUES(?, ?)",
				friendshipsTable, FriendshipsColumns.FIRST_USERNAME.toString()
						.toLowerCase(), FriendshipsColumns.SECOND_USERNAME
						.toString().toLowerCase());

		gettingQuery = String
				.format("SELECT %s, %s, %s, %s FROM (SELECT * FROM %s WHERE %s = ?) O JOIN %s ON (O.%s = %s.%s)",
						UsersColumns.USERNAME.toString().toLowerCase(),
						UsersColumns.PASSWORD.toString().toLowerCase(),
						UsersColumns.NAME.toString().toLowerCase(),
						UsersColumns.BALANCE.toString().toLowerCase(),
						friendshipsTable, FriendshipsColumns.FIRST_USERNAME
								.toString().toLowerCase(), usersTable,
						FriendshipsColumns.SECOND_USERNAME.toString()
								.toLowerCase(), usersTable,
						UsersColumns.USERNAME.toString().toLowerCase());

		areFriendsQuery = String
				.format("SELECT CASE WHEN EXISTS (SELECT * FROM %s WHERE %s=? AND %s=?) THEN 'TRUE' ELSE 'FALSE' END;",
						friendshipsTable, FriendshipsColumns.FIRST_USERNAME
								.toString().toLowerCase(),
						FriendshipsColumns.SECOND_USERNAME.toString()
								.toLowerCase());

		deleteUserFriendsQuery = String.format(
				"DELETE FROM %s WHERE ? IN (%s.%s,%s.%s);", friendshipsTable,
				friendshipsTable, FriendshipsColumns.FIRST_USERNAME.toString()
						.toLowerCase(), friendshipsTable,
				FriendshipsColumns.SECOND_USERNAME.toString().toLowerCase());

	}

	private void insertByUsername(String username1, String username2,
			Connection conn) throws ElementAlreadyExistsException,
			DatabaseUnkownFailureException, InvalidParamsException {
		if (isConnClosed(conn)) {
			throw new InvalidParamsException();
		}

		try (PreparedStatement prpdStmt = conn.prepareStatement(insertionQuery)) {
			prpdStmt.setString(1, username1);
			prpdStmt.setString(2, username2);
			prpdStmt.executeUpdate();
			prpdStmt.setString(1, username2);
			prpdStmt.setString(2, username1);
			prpdStmt.executeUpdate();
		} catch (SQLException e) {
			if (e.getErrorCode() == MysqlErrorNumbers.ER_DUP_ENTRY) {
				throw new ElementAlreadyExistsException(e);
			}

			throw new DatabaseUnkownFailureException(e);
		}
	}

	private List<DBUser> getByUsername(String username)
			throws DatabaseUnkownFailureException {
		ArrayList<DBUser> $ = new ArrayList<>();

		try (Connection conn = getConnection();
				PreparedStatement prpdStmt = conn
						.prepareStatement(gettingQuery)) {
			conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);

			prpdStmt.setString(1, username);
			ResultSet res = prpdStmt.executeQuery();

			while (res.next()) {
				DBUser u = new DBUser(res.getString(UsersColumns.USERNAME
						.toString().toLowerCase()),
						res.getString(UsersColumns.PASSWORD.toString()
								.toLowerCase()),
						res.getString(UsersColumns.NAME.toString()
								.toLowerCase()),
						res.getInt(UsersColumns.BALANCE.toString()
								.toLowerCase()));

				$.add(u);
			}
		} catch (SQLException e) {
			throw new DatabaseUnkownFailureException(e);
		}

		return $;
	}

	private static boolean isUserNull(DBUser u) {
		return (u == null || u.getUsername() == null || u.getPassword() == null || u
				.getName() == null);
	}

	private final String friendshipsTable;

	private final String usersTable;

	private String creationQuery;

	private String insertionQuery;

	private String gettingQuery;

	private String areFriendsQuery;

	private String deleteUserFriendsQuery;

}

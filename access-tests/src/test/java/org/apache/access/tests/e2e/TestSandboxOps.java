/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.access.tests.e2e;

import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.hadoop.fs.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Resources;

public class TestSandboxOps  extends AbstractTestWithStaticDFS {
  private static final String SINGLE_TYPE_DATA_FILE_NAME = "kv1.dat";
  private static final String ADMIN = "admin1";
  private static final String ALL_DB1 = "server=server1->db=db_1",
      ALL_DB2 = "server=server1->db=db_2",
      SELECT_DB1_TBL1 = "server=server1->db=db_1->table=tb_1->action=select",
      SELECT_DB1_TBL2 = "server=server1->db=db_1->table=tb_2->action=select",
      INSERT_DB1_TBL1 = "server=server1->db=db_1->table=tb_1->action=insert",
      SELECT_DB2_TBL2 = "server=server1->db=db_2->table=tb_2->action=select",
      USER1 = "user1",
      USER2 = "user2",
      GROUP1 = "group1",
      GROUP1_ROLE = "group1_role",
      DB1 = "db_1",
      DB2 = "db_2",
      DB3 = "db_3",
      TBL1 = "tb_1",
      TBL2 = "tb_2",
      TBL3 = "tb_3",
      VIEW1 = "view_1",
      VIEW2 = "view_2",
      VIEW3 = "view_3",
      INDEX1 = "index_1";



  private Context context;
  private PolicyFile policyFile;
  private File dataFile;
  private String loadData;


  @Before
  public void setup() throws Exception {
    context = createContext();
    dataFile = new File(dataDir, SINGLE_TYPE_DATA_FILE_NAME);
    FileOutputStream to = new FileOutputStream(dataFile);
    Resources.copy(Resources.getResource(SINGLE_TYPE_DATA_FILE_NAME), to);
    to.close();
    policyFile = PolicyFile.createAdminOnServer1(ADMIN);

    loadData = "server=server1->uri=file:" + dataFile.getPath();
  }

  @After
  public void tearDown() throws Exception {
    if (context != null) {
      context.close();
    }
  }
  private PolicyFile addTwoUsersWithAllDb() {
    policyFile
    .addGroupsToUser("user1", "user_group")
    .addGroupsToUser("user2", "user_group")
    .addPermissionsToRole("db1_all", "server=server1->db=db1")
    .addPermissionsToRole("db2_all", "server=server1->db=db2")
    .addRolesToGroup("user_group", "db1_all", "db2_all");
    return policyFile;
  }
  private void dropDb(String user, String...dbs) throws Exception {
    Connection connection = context.createConnection(user, "password");
    Statement statement = connection.createStatement();
    for(String db : dbs) {
      statement.execute("DROP DATABASE IF EXISTS " + db + " CASCADE");
    }
    statement.close();
    connection.close();
  }
  private void createDb(String user, String...dbs) throws Exception {
    Connection connection = context.createConnection(user, "password");
    Statement statement = connection.createStatement();
    for(String db : dbs) {
      statement.execute("CREATE DATABASE " + db);
    }
    statement.close();
    connection.close();
  }
  private void createTable(String user, String db, String...tables) throws Exception {
    Connection connection = context.createConnection(user, "password");
    Statement statement = connection.createStatement();
    statement.execute("USE " + db);
    for(String table : tables) {
      statement.execute("DROP TABLE IF EXISTS " + table);
      statement.execute("create table " + table
          + " (under_col int comment 'the under column', value string)");
      statement.execute("load data local inpath '" + dataFile.getPath()
          + "' into table " + table);
    }
    statement.close();
    connection.close();
  }
  /**
   * Tests to ensure that users with all@db can create tables
   * and that they cannot create databases or load data
   */
  @Test
  public void testDbPrivileges() throws Exception {
    addTwoUsersWithAllDb().write(context.getPolicyFile());
    String[] dbs = new String[] { "db1", "db2" };
    for (String dbName : dbs) {
      dropDb(ADMIN, dbName);
      createDb(ADMIN, dbName);
    }
    for (String user : new String[] { "user1", "user2" }) {
      for (String dbName : new String[] { "db1", "db2" }) {
        Connection userConn = context.createConnection(user, "foo");
        String tabName = user + "_tab1";
        Statement userStmt = context.createStatement(userConn);
        // Positive case: test user1 and user2 has
        //  permissions to access db1 and db2
        userStmt.execute("use " + dbName);
        userStmt.execute("create table " + tabName + " (id int)");
        context.assertAuthzException(userStmt, "load data local inpath '" + dataFile + "' into table " + tabName);
        assertTrue(userStmt.execute("select * from " + tabName));
        // negative users cannot create databases
        context.assertAuthzException(userStmt, "CREATE DATABASE " + user + "_db");
        userStmt.close();
        userConn.close();
      }
    }

    for (String dbName : dbs) {
      dropDb(ADMIN, dbName);
    }

  }
  /**
   * Test Case 2.11 admin user create a new database DB_1 and grant ALL to
   * himself on DB_1 should work
   */
  @Test
  public void testAdminDbPrivileges() throws Exception {
    policyFile.write(context.getPolicyFile());
    Connection adminCon = context.createConnection(ADMIN, "password");
    Statement adminStmt = context.createStatement(adminCon);
    String dbName = "db1";
    adminStmt.execute("use default");
    adminStmt.execute("DROP DATABASE IF EXISTS " + dbName + " CASCADE");
    adminStmt.execute("CREATE DATABASE " + dbName);

    // access the new databases
    adminStmt.execute("use " + dbName);
    String tabName = "admin_tab1";
    adminStmt.execute("create table " + tabName + "(c1 string)");
    adminStmt.execute("load data local inpath '/etc/passwd' into table "
        + tabName);
    adminStmt.execute("select * from " + tabName);

    // cleanup
    adminStmt.execute("use default");
    adminStmt.execute("DROP DATABASE " + dbName + " CASCADE");
    adminStmt.close();
    adminCon.close();
  }

  /**
   * Test Case 2.16 admin user create a new database DB_1 create TABLE_1 and
   * TABLE_2 (same schema) in DB_1 admin user grant SELECT, INSERT to USER_1's
   * group on TABLE_2 negative test case: USER_1 try to do following on TABLE_1
   * will fail: --insert overwrite TABLE_2 select * from TABLE_1
   */
  @Test
  public void testNegativeUserDMLPrivileges() throws Exception {
    policyFile
    .addPermissionsToRole("db1_tab2_all", "server=server1->db=db1->table=table_2")
    .addRolesToGroup("group1", "db1_tab2_all")
    .addGroupsToUser("user3", "group1");
    policyFile.write(context.getPolicyFile());
    Connection adminCon = context.createConnection(ADMIN, "password");
    Statement adminStmt = context.createStatement(adminCon);
    String dbName = "db1";
    adminStmt.execute("use default");
    adminStmt.execute("DROP DATABASE IF EXISTS " + dbName + " CASCADE");
    adminStmt.execute("CREATE DATABASE " + dbName);
    adminStmt.execute("use " + dbName);
    adminStmt.execute("create table table_1 (id int)");
    adminStmt.execute("create table table_2 (id int)");
    adminStmt.close();
    adminCon.close();
    Connection userConn = context.createConnection("user3", "password");
    Statement userStmt = context.createStatement(userConn);
    userStmt.execute("use " + dbName);
    // user3 doesn't have select privilege on table_1, so insert/select should fail
    context.assertAuthzException(userStmt, "insert overwrite table table_2 select * from table_1");
    context.assertAuthzException(userStmt, "insert overwrite directory '" + baseDir.getPath() + "' select * from table_1");
    userConn.close();
    userStmt.close();
  }

  /**
   * Test Case 2.17 Execution steps a) Admin user creates a new database DB_1,
   * b) Admin user grants ALL on DB_1 to group GROUP_1 c) User from GROUP_1
   * creates table TAB_1, TAB_2 in DB_1 d) Admin user grants SELECT on TAB_1 to
   * group GROUP_2
   *
   * 1) verify users from GROUP_2 have only SELECT privileges on TAB_1. They
   * shouldn't be able to perform any operation other than those listed as
   * requiring SELECT in the privilege model.
   *
   * 2) verify users from GROUP_2 can't perform queries involving join between
   * TAB_1 and TAB_2.
   *
   * 3) verify users from GROUP_1 can't perform operations requiring ALL @
   * SERVER scope. Refer to list
   */
  @Test
  public void testNegUserPrivilegesAll() throws Exception {
    String testPolicies[] = {
        "[groups]",
        "admin_group = admin_role",
        "user_group1 = db1_all",
        "user_group2 = db1_tab1_select",
        "[roles]",
        "db1_all = server=server1->db=db1",
        "db1_tab1_select = server=server1->db=db1->table=table_1->action=select",
        "admin_role = server=server1",
        "[users]",
        "user1 = user_group1",
        "user2 = user_group2",
        "admin = admin_group"
    };
    context.makeNewPolicy(testPolicies);

    // create dbs
    Connection adminCon = context.createConnection("admin", "foo");
    Statement adminStmt = context.createStatement(adminCon);
    String dbName = "db1";
    adminStmt.execute("use default");
    adminStmt.execute("DROP DATABASE IF EXISTS " + dbName + " CASCADE");
    adminStmt.execute("CREATE DATABASE " + dbName);
    adminStmt.execute("use " + dbName);
    adminStmt.execute("create table table_1 (name string)");
    adminStmt.execute("load data local inpath '" + dataFile.getPath() + "' into table table_1");
    adminStmt.execute("create table table_2 (name string)");
    adminStmt.execute("load data local inpath '" + dataFile.getPath() + "' into table table_2");
    adminStmt.execute("create view v1 AS select * from table_1");
    adminStmt.execute("create table table_part_1 (name string) PARTITIONED BY (year INT)");
    adminStmt.execute("ALTER TABLE table_part_1 ADD PARTITION (year = 2012)");
    adminStmt.close();
    adminCon.close();

    Connection userConn = context.createConnection("user2", "foo");
    Statement userStmt = context.createStatement(userConn);
    userStmt.execute("use " + dbName);

    context.assertAuthzException(userStmt, "alter table table_2 add columns (id int)");
    context.assertAuthzException(userStmt, "drop database " + dbName);
    context.assertAuthzException(userStmt, "CREATE INDEX x ON TABLE table_1(name) AS 'org.apache.hadoop.hive.ql.index.compact.CompactIndexHandler'");
    context.assertAuthzException(userStmt, "CREATE TEMPORARY FUNCTION strip AS 'org.apache.hadoop.hive.ql.udf.generic.GenericUDFPrintf'");
    context.assertAuthzException(userStmt, "create table c_tab_2 as select * from table_2");
    context.assertAuthzException(userStmt, "ALTER DATABASE " + dbName + " SET DBPROPERTIES ('foo' = 'bar')");
    context.assertAuthzException(userStmt, "ALTER VIEW v1 SET TBLPROPERTIES ('foo' = 'bar')");
    context.assertAuthzException(userStmt, "DROP VIEW IF EXISTS v1");
    context.assertAuthzException(userStmt, "create table table_5 (name string)");
    context.assertAuthzException(userStmt, "ALTER TABLE table_1  RENAME TO table_99");
    context.assertAuthzException(userStmt, "insert overwrite table table_2 select * from table_1");
    context.assertAuthzException(userStmt, "ALTER TABLE table_part_1 ADD IF NOT EXISTS PARTITION (year = 2012)");
    context.assertAuthzException(userStmt, "ALTER TABLE table_part_1 PARTITION (year = 2012) SET LOCATION '" + baseDir.getPath() + "'");
  }

  /**
   * Steps:
   * 1. admin user create databases, DB_1 and DB_2, no table or other
   * object in database
   * 2. admin grant all to USER_1's group on DB_1 and DB_2
   *   positive test case:
   *     a)USER_1 has the privilege to create table, load data,
   *     drop table, create view, insert more data on both databases
   *     b) USER_1 can switch between DB_1 and DB_2 without
   *     exception negative test case:
   *     c) USER_1 cannot drop database
   * 3. admin remove all to group1 on DB_2
   *   positive test case:
   *     d) USER_1 has the privilege to create view on tables in DB_1
   *   negative test case:
   *     e) USER_1 cannot create view on tables in DB_1 that select
   *     from tables in DB_2
   * 4. admin grant select to group1 on DB_2.ta_2
   *   positive test case:
   *     f) USER_1 has the privilege to create view to select from
   *     DB_1.tb_1 and DB_2.tb_2
   *   negative test case:
   *     g) USER_1 cannot create view to select from DB_1.tb_1
   *     and DB_2.tb_3
   * @throws Exception
   */
  @Test
  public void testSandboxOpt9() throws Exception {

    policyFile
    .addPermissionsToRole(GROUP1_ROLE, ALL_DB1, ALL_DB2, loadData)
    .addRolesToGroup(GROUP1, GROUP1_ROLE)
    .addGroupsToUser(USER1, GROUP1);
    policyFile.write(context.getPolicyFile());

    dropDb(ADMIN, DB1, DB2);
    createDb(ADMIN, DB1, DB2);

    Connection connection = context.createConnection(USER1, "password");
    Statement statement = context.createStatement(connection);

    // a
    statement.execute("USE " + DB1);
    createTable(USER1, DB1, TBL1);
    statement.execute("DROP VIEW IF EXISTS " + VIEW1);
    statement.execute("CREATE VIEW " + VIEW1 + " (value) AS SELECT value from " + TBL1 + " LIMIT 10");

    createTable(USER1, DB2, TBL2, TBL3);
    // c
    context.assertAuthzException(statement, "DROP DATABASE IF EXISTS " + DB1 + " CASCADE");
    context.assertAuthzException(statement, "DROP DATABASE IF EXISTS " + DB2 + " CASCADE");
    // d
    statement.execute("USE " + DB1);
    policyFile.removePermissionsFromRole(GROUP1_ROLE, ALL_DB2);
    policyFile.write(context.getPolicyFile());
    // e
    statement.execute("DROP VIEW IF EXISTS " + VIEW2);
    context.assertAuthzException(statement, "CREATE VIEW " + VIEW2 +
        " (value) AS SELECT value from " + DB2 + "." + TBL2 + " LIMIT 10");
    // f
    policyFile.addPermissionsToRole(GROUP1_ROLE, SELECT_DB2_TBL2);
    policyFile.write(context.getPolicyFile());
    statement.execute("DROP VIEW IF EXISTS " + VIEW2);
    statement.execute("CREATE VIEW " + VIEW2
        + " (value) AS SELECT value from " + DB2 + "." + TBL2 + " LIMIT 10");

    // g
    statement.execute("DROP VIEW IF EXISTS " + VIEW3);
    context.assertAuthzException(statement, "CREATE VIEW " + VIEW3
        + " (value) AS SELECT value from " + DB2 + "." + TBL3 + " LIMIT 10");
    statement.close();
    connection.close();
    dropDb(ADMIN, DB1, DB2);
  }

  /**
   * Tests select on table with index.
   *
   * Steps:
   * 1. admin user create a new database DB_1
   * 2. admin create TABLE_1 in DB_1
   * 3. admin create INDEX_1 for COLUMN_1 in TABLE_1 in DB_1
   * 4. admin user grant INSERT and SELECT to USER_1's group on TABLE_1
   * 5. admin user doesn't grant SELECT to USER_1's group on INDEX_1
   *   negative test case:
   *     a) USER_1 try to SELECT * FROM TABLE_1 WHERE COLUMN_1 == ...
   *     should NOT work
   *     b) USER_1 should not be able to check the list of view or
   *     index in DB_1
   * @throws Exception
   */
  @Test
  public void testSandboxOpt13() throws Exception {
    // unrelated permission to allow user1 to connect to db1
    policyFile
    .addPermissionsToRole(GROUP1_ROLE, SELECT_DB1_TBL2)
    .addRolesToGroup(GROUP1, GROUP1_ROLE)
    .addGroupsToUser(USER1, GROUP1);
    policyFile.write(context.getPolicyFile());
    dropDb(ADMIN, DB1);
    createDb(ADMIN, DB1);
    createTable(ADMIN, DB1, TBL1);
    Connection connection = context.createConnection(ADMIN, "password");
    Statement statement = context.createStatement(connection);
    statement.execute("USE " + DB1);
    statement.execute("DROP INDEX IF EXISTS " + INDEX1 + " ON " + TBL1);
    statement.execute("CREATE INDEX " + INDEX1 + " ON TABLE " + TBL1
        + " (under_col) as 'COMPACT' WITH DEFERRED REBUILD");
    statement.close();
    connection.close();
    connection = context.createConnection(USER1, "password");
    statement = context.createStatement(connection);
    statement.execute("USE " + DB1);
    context.assertAuthzException(statement, "SELECT * FROM " + TBL1 + " WHERE under_col == 5");
    context.assertAuthzException(statement, "SHOW INDEXES ON " + TBL1);
    policyFile.addPermissionsToRole(GROUP1_ROLE, SELECT_DB1_TBL1, INSERT_DB1_TBL1, loadData);
    policyFile.write(context.getPolicyFile());
    statement.execute("USE " + DB1);
    assertTrue(statement.execute("SELECT * FROM " + TBL1 + " WHERE under_col == 5"));
    assertTrue(statement.execute("SHOW INDEXES ON " + TBL1));
    policyFile.write(context.getPolicyFile());
    dropDb(ADMIN, DB1, DB2);
  }

  /**
   * Steps:
   * 1. Admin user creates a new database DB_1
   * 2. Admin user grants ALL on DB_1 to group GROUP_1
   * 3. User from GROUP_1 creates table TAB_1, TAB_2 in DB_1
   * 4. Admin user grants SELECT/INSERT on TAB_1 to group GROUP_2
   *   a) verify users from GROUP_2 have only SELECT/INSERT
   *     privileges on TAB_1. They shouldn't be able to perform
   *     any operation other than those listed as
   *     requiring SELECT in the privilege model.
   *   b) verify users from GROUP_2 can't perform queries
   *     involving join between TAB_1 and TAB_2.
   *   c) verify users from GROUP_1 can't perform operations
   *     requiring ALL @SERVER scope:
   *     *) create database
   *     *) drop database
   *     *) show databases
   *     *) show locks
   *     *) execute ALTER TABLE .. SET LOCATION on a table in DB_1
   *     *) execute ALTER PARTITION ... SET LOCATION on a table in DB_1
   *     *) execute CREATE EXTERNAL TABLE ... in DB_1
   *     *) execute ADD JAR
   *     *) execute a query with TRANSOFORM
   * @throws Exception
   */
  @Test
  public void testSandboxOpt17() throws Exception {
    // edit policy file
    File policyFile = context.getPolicyFile();
    PolicyFileEditor editor = new PolicyFileEditor(policyFile);
    editor.addPolicy("admin = admin", "groups");
    editor.addPolicy("group1 = all_db1, load_data", "groups");
    editor.addPolicy("group2 = select_tb1", "groups");
    editor.addPolicy("select_tb1 = server=server1->db=db_1->table=tbl_1->action=select", "roles");
    editor.addPolicy("all_db1 = server=server1->db=db_1", "roles");
    editor.addPolicy("load_data = server=server1->uri=file:" + dataFile.toString(), "roles");
    editor.addPolicy("admin = server=server1", "roles");
    editor.addPolicy("admin1 = admin", "users");
    editor.addPolicy("user1 = group1", "users");
    editor.addPolicy("user2 = group2", "users");

    dropDb(ADMIN, DB1);
    createDb(ADMIN, DB1);

    createTable(USER1, DB1, TBL1, TBL2);
    Connection connection = context.createConnection(USER1, "password");
    Statement statement = context.createStatement(connection);
    // c
    statement.execute("USE " + DB1);
    context.assertAuthzException(statement, "CREATE DATABASE " + DB3);
    context.assertAuthzException(statement, "DROP DATABASE " + DB1);
    ResultSet rs = statement.executeQuery("SHOW DATABASES");
    assertTrue(rs.next());
    assertEquals(DB1, rs.getString(1));
    context.assertAuthzException(statement, "ALTER TABLE " + TBL1 +
        " ADD PARTITION (value = 10) LOCATION '" + dataDir.getPath() + "'");
    context.assertAuthzException(statement, "ALTER TABLE " + TBL1
        + " PARTITION (value = 10) SET LOCATION '" + dataDir.getPath() + "'");
    context.assertAuthzException(statement, "CREATE EXTERNAL TABLE " + TBL3
        + " (under_col int, value string) LOCATION '" + dataDir.getPath() + "'");
    context.assertAuthzException(statement, "ADD JAR /usr/lib/hive/lib/hbase.jar");
    statement.close();
    connection.close();

    connection = context.createConnection(USER2, "password");
    statement = context.createStatement(connection);

    // a
    statement.execute("USE " + DB1);
    context.assertAuthzException(statement, "SELECT * FROM TABLE " + TBL2 + " LIMIT 10");
    context.assertAuthzException(statement, "EXPLAIN SELECT * FROM TABLE " + TBL2 + " WHERE under_col > 5 LIMIT 10");
    context.assertAuthzException(statement, "DESCRIBE " + TBL2);
    context.assertAuthzException(statement, "LOAD DATA LOCAL INPATH '" + dataFile.getPath() + "' INTO TABLE " + TBL2);
    context.assertAuthzException(statement, "analyze table " + TBL2 + " compute statistics for columns under_col, value");
    // b
    context.assertAuthzException(statement, "SELECT " + TBL1 + ".* FROM " + TBL1 + " JOIN " + TBL2 +
        " ON (" + TBL1 + ".value = " + TBL2 + ".value)");
    statement.close();
    connection.close();
  }

  /**
   * Positive and negative tests for INSERT OVERWRITE [LOCAL] DIRECTORY and
   * LOAD DATA [LOCAL] INPATH. EXPORT/IMPORT are handled in seperate junit class.
   * Formerly testSandboxOpt18
   */
  @Test
  public void testInsertOverwriteAndLoadData() throws Exception {
    long counter = System.currentTimeMillis();
    File allowedDir = assertCreateDir(new File(baseDir,
        "test-" + (counter++)));
    File restrictedDir = assertCreateDir(new File(baseDir,
        "test-" + (counter++)));
    Path allowedDfsDir = assertCreateDfsDir(new Path(dfsBaseDir, "test-" + (counter++)));
    Path restrictedDfsDir = assertCreateDfsDir(new Path(dfsBaseDir, "test-" + (counter++)));
    File policyFile = context.getPolicyFile();
    PolicyFileEditor editor = new PolicyFileEditor(policyFile);
    editor.addPolicy("admin = admin", "groups");
    editor.addPolicy("group1 = all_db1, load_data", "groups");
    editor.addPolicy("all_db1 = server=server1->db=db_1", "roles");
    editor.addPolicy("admin = server=server1", "roles");
    editor.addPolicy("load_data = server=server1->uri=file://" + allowedDir.getPath() +
        ", server=server1->uri=file:" + allowedDir.getPath() +
        ", server=server1->uri=" + allowedDfsDir.toString(), "roles");
    editor.addPolicy("admin1 = admin", "users");
    editor.addPolicy("user1 = group1", "users");
    dropDb(ADMIN, DB1);
    createDb(ADMIN, DB1);
    createTable(ADMIN, DB1, TBL1);
    Connection connection = context.createConnection(USER1, "password");
    Statement statement = context.createStatement(connection);
    statement.execute("USE " + DB1);
    statement.execute("INSERT OVERWRITE LOCAL DIRECTORY 'file://" +
        allowedDir.getPath() + "' SELECT * FROM " + TBL1);
    statement.execute("INSERT OVERWRITE DIRECTORY '" + allowedDfsDir + "' SELECT * FROM " + TBL1);
    statement.execute("LOAD DATA LOCAL INPATH 'file://" + allowedDir.getPath()
        + "' INTO TABLE " + TBL1);
    statement.execute("LOAD DATA INPATH 'file://" + allowedDir.getPath()
        + "' INTO TABLE " + TBL1);
    context.assertAuthzException(statement, "INSERT OVERWRITE LOCAL DIRECTORY 'file://" +
        restrictedDir.getPath() + "' SELECT * FROM " + TBL1);
    context.assertAuthzException(statement, "INSERT OVERWRITE DIRECTORY '" + restrictedDfsDir + "' SELECT * FROM " + TBL1);
    context.assertAuthzException(statement, "LOAD DATA INPATH 'file://" + restrictedDir.getPath()
        + "' INTO TABLE " + TBL1);
    context.assertAuthzException(statement, "LOAD DATA LOCAL INPATH 'file://" + restrictedDir.getPath()
        + "' INTO TABLE " + TBL1);
    statement.close();
    connection.close();
  }
}
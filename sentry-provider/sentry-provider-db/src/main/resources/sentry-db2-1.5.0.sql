--Licensed to the Apache Software Foundation (ASF) under one or more
--contributor license agreements.  See the NOTICE file distributed with
--this work for additional information regarding copyright ownership.
--The ASF licenses this file to You under the Apache License, Version 2.0
--(the "License"); you may not use this file except in compliance with
--the License.  You may obtain a copy of the License at
--
--    http://www.apache.org/licenses/LICENSE-2.0
--
--Unless required by applicable law or agreed to in writing, software
--distributed under the License is distributed on an "AS IS" BASIS,
--WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
--See the License for the specific language governing permissions and
--limitations under the License.

-- Table SENTRY_DB_PRIVILEGE for classes [org.apache.sentry.provider.db.service.model.MSentryPrivilege]
CREATE TABLE SENTRY_DB_PRIVILEGE
(
    DB_PRIVILEGE_ID BIGINT NOT NULL generated always as identity (start with 1),
    URI VARCHAR(4000),
    "ACTION" VARCHAR(40),
    CREATE_TIME BIGINT NOT NULL,
    DB_NAME VARCHAR(4000),
    GRANTOR_PRINCIPAL VARCHAR(4000),
    PRIVILEGE_SCOPE VARCHAR(40),
    "SERVER_NAME" VARCHAR(4000),
    "TABLE_NAME" VARCHAR(4000),
    WITH_GRANT_OPTION CHAR(1) NOT NULL
);

ALTER TABLE SENTRY_DB_PRIVILEGE ADD CONSTRAINT SENTRY_DB_PRIVILEGE_PK PRIMARY KEY (DB_PRIVILEGE_ID);

-- Table SENTRY_ROLE for classes [org.apache.sentry.provider.db.service.model.MSentryRole]
CREATE TABLE SENTRY_ROLE
(
    ROLE_ID BIGINT NOT NULL generated always as identity (start with 1),
    CREATE_TIME BIGINT NOT NULL,
    GRANTOR_PRINCIPAL VARCHAR(4000),
    ROLE_NAME VARCHAR(128)
);

ALTER TABLE SENTRY_ROLE ADD CONSTRAINT SENTRY_ROLE_PK PRIMARY KEY (ROLE_ID);

-- Table SENTRY_GROUP for classes [org.apache.sentry.provider.db.service.model.MSentryGroup]
CREATE TABLE SENTRY_GROUP
(
    GROUP_ID BIGINT NOT NULL generated always as identity (start with 1),
    CREATE_TIME BIGINT NOT NULL,
    GRANTOR_PRINCIPAL VARCHAR(4000),
    GROUP_NAME VARCHAR(128)
);

ALTER TABLE SENTRY_GROUP ADD CONSTRAINT SENTRY_GROUP_PK PRIMARY KEY (GROUP_ID);

-- Table SENTRY_ROLE_GROUP_MAP for join relationship
CREATE TABLE SENTRY_ROLE_GROUP_MAP
(
    GROUP_ID BIGINT NOT NULL,
    ROLE_ID BIGINT NOT NULL
);

ALTER TABLE SENTRY_ROLE_GROUP_MAP ADD CONSTRAINT SENTRY_ROLE_GROUP_MAP_PK PRIMARY KEY (GROUP_ID,ROLE_ID);

-- Table SENTRY_ROLE_DB_PRIVILEGE_MAP for join relationship
CREATE TABLE SENTRY_ROLE_DB_PRIVILEGE_MAP
(
    ROLE_ID BIGINT NOT NULL,
    DB_PRIVILEGE_ID BIGINT NOT NULL
);

ALTER TABLE SENTRY_ROLE_DB_PRIVILEGE_MAP ADD CONSTRAINT SENTRY_ROLE_DB_PRIVILEGE_MAP_PK PRIMARY KEY (ROLE_ID,DB_PRIVILEGE_ID);

CREATE TABLE "SENTRY_VERSION" (
  VER_ID BIGINT NOT NULL,
  SCHEMA_VERSION VARCHAR(127),
  VERSION_COMMENT VARCHAR(255)
);

ALTER TABLE SENTRY_VERSION ADD CONSTRAINT SENTRY_VERSION_PK PRIMARY KEY (VER_ID);

-- Constraints for table SENTRY_DB_PRIVILEGE for class(es) [org.apache.sentry.provider.db.service.model.MSentryPrivilege]
CREATE UNIQUE INDEX SENTRYPRIVILEGENAME ON SENTRY_DB_PRIVILEGE ("SERVER_NAME",DB_NAME,"TABLE_NAME",URI,"ACTION",WITH_GRANT_OPTION);


-- Constraints for table SENTRY_ROLE for class(es) [org.apache.sentry.provider.db.service.model.MSentryRole]
CREATE UNIQUE INDEX SENTRYROLENAME ON SENTRY_ROLE (ROLE_NAME);


-- Constraints for table SENTRY_GROUP for class(es) [org.apache.sentry.provider.db.service.model.MSentryGroup]
CREATE UNIQUE INDEX SENTRYGROUPNAME ON SENTRY_GROUP (GROUP_NAME);


-- Constraints for table SENTRY_ROLE_GROUP_MAP
CREATE INDEX SENTRY_ROLE_GROUP_MAP_N49 ON SENTRY_ROLE_GROUP_MAP (GROUP_ID);

CREATE INDEX SENTRY_ROLE_GROUP_MAP_N50 ON SENTRY_ROLE_GROUP_MAP (ROLE_ID);

ALTER TABLE SENTRY_ROLE_GROUP_MAP ADD CONSTRAINT SENTRY_ROLE_GROUP_MAP_FK2 FOREIGN KEY (ROLE_ID) REFERENCES SENTRY_ROLE (ROLE_ID) ;

ALTER TABLE SENTRY_ROLE_GROUP_MAP ADD CONSTRAINT SENTRY_ROLE_GROUP_MAP_FK1 FOREIGN KEY (GROUP_ID) REFERENCES SENTRY_GROUP (GROUP_ID) ;


-- Constraints for table SENTRY_ROLE_DB_PRIVILEGE_MAP
CREATE INDEX SENTRY_ROLE_DB_PRIVILEGE_MAP_N50 ON SENTRY_ROLE_DB_PRIVILEGE_MAP (ROLE_ID);

CREATE INDEX SENTRY_ROLE_DB_PRIVILEGE_MAP_N49 ON SENTRY_ROLE_DB_PRIVILEGE_MAP (DB_PRIVILEGE_ID);

ALTER TABLE SENTRY_ROLE_DB_PRIVILEGE_MAP ADD CONSTRAINT SENTRY_ROLE_DB_PRIVILEGE_MAP_FK2 FOREIGN KEY (DB_PRIVILEGE_ID) REFERENCES SENTRY_DB_PRIVILEGE (DB_PRIVILEGE_ID) ;

ALTER TABLE SENTRY_ROLE_DB_PRIVILEGE_MAP ADD CONSTRAINT SENTRY_ROLE_DB_PRIVILEGE_MAP_FK1 FOREIGN KEY (ROLE_ID) REFERENCES SENTRY_ROLE (ROLE_ID) ;

INSERT INTO SENTRY_VERSION (VER_ID, SCHEMA_VERSION, VERSION_COMMENT) VALUES (1, '1.5.0', 'Sentry release version 1.5.0');
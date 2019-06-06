/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2015 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.parosproxy.paros.db.paros;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.parosproxy.paros.db.DatabaseException;
import org.parosproxy.paros.db.DbUtils;
import org.parosproxy.paros.db.RecordStructure;
import org.parosproxy.paros.db.TableStructure;

public class ParosTableStructure extends ParosAbstractTable implements TableStructure {

    private static final String TABLE_NAME = "STRUCTURE";

    private static final String STRUCTUREID = "STRUCTUREID";
    private static final String SESSIONID = "SESSIONID";
    private static final String PARENTID = "PARENTID";
    private static final String HISTORYID = "HISTORYID";
    private static final String NAME = "NAME";
    private static final String NAMEHASH = "NAMEHASH";
    private static final String URL = "URL";
    private static final String METHOD = "METHOD";

    private PreparedStatement psRead = null;
    private PreparedStatement psFind = null;
    private PreparedStatement psInsert = null;
    private CallableStatement psGetIdLastInsert = null;
    private PreparedStatement psGetChildren = null;
    private PreparedStatement psGetChildCount = null;

    public ParosTableStructure() {}

    /*
     * 	public RecordStructure(long structureId, long parentId, int historyId, String url) {
     */

    @Override
    protected void reconnect(Connection conn) throws DatabaseException {
        try {
            if (!DbUtils.hasTable(conn, TABLE_NAME)) {
                // Need to create the table
                DbUtils.execute(
                        conn,
                        "CREATE cached TABLE STRUCTURE (STRUCTUREID bigint generated by default as identity (start with 1), "
                                + "SESSIONID bigint not null, PARENTID bigint not null, HISTORYID int, "
                                + "NAME varchar(8192) not null, NAMEHASH bigint not null, "
                                + "URL varchar(8192) not null, METHOD varchar(10) not null)");
            }

            psRead =
                    conn.prepareStatement(
                            "SELECT * FROM STRUCTURE WHERE SESSIONID = ? AND STRUCTUREID = ?");

            psFind =
                    conn.prepareStatement(
                            "SELECT * FROM STRUCTURE WHERE SESSIONID = ? AND NAMEHASH = ? AND METHOD = ?");

            psInsert =
                    conn.prepareStatement(
                            "INSERT INTO STRUCTURE (SESSIONID, PARENTID, HISTORYID, NAME, NAMEHASH, URL, METHOD) VALUES (?, ?, ?, ?, ?, ?, ?)");
            psGetIdLastInsert = conn.prepareCall("CALL IDENTITY();");

            psGetChildren =
                    conn.prepareStatement(
                            "SELECT * FROM STRUCTURE WHERE SESSIONID = ? AND PARENTID = ?");

            psGetChildCount =
                    conn.prepareStatement(
                            "SELECT COUNT(*) FROM STRUCTURE WHERE SESSIONID = ? AND PARENTID = ?");

        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    /* (non-Javadoc)
     * @see org.parosproxy.paros.db.paros.TableParam#read(long)
     */
    @Override
    public synchronized RecordStructure read(long sessionId, long urlId) throws DatabaseException {
        try {
            psRead.setLong(1, sessionId);
            psRead.setLong(2, urlId);

            try (ResultSet rs = psRead.executeQuery()) {
                RecordStructure result = build(rs);
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    public RecordStructure insert(
            long sessionId, long parentId, int historyId, String name, String url, String method)
            throws DatabaseException {
        try {
            psInsert.setLong(1, sessionId);
            psInsert.setLong(2, parentId);
            psInsert.setInt(3, historyId);
            psInsert.setString(4, name);
            psInsert.setInt(5, name.hashCode());
            psInsert.setString(6, url);
            psInsert.setString(7, method);
            psInsert.executeUpdate();

            long id;
            try (ResultSet rs = psGetIdLastInsert.executeQuery()) {
                rs.next();
                id = rs.getLong(1);
            }
            return read(sessionId, id);
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    public RecordStructure find(long sessionId, String name, String method)
            throws DatabaseException {
        try {
            psFind.setLong(1, sessionId);
            psFind.setInt(2, name.hashCode());
            psFind.setString(3, method);
            try (ResultSet rs = psFind.executeQuery()) {
                while (rs.next()) {
                    // We can get multiple records back due to hash collisions,
                    // so double check the actual URL
                    if (name.equals(rs.getString(NAME))) {
                        return build(rs);
                    }
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }

        return null;
    }

    @Override
    public List<RecordStructure> getChildren(long sessionId, long parentId)
            throws DatabaseException {
        try {
            psGetChildren.setLong(1, sessionId);
            psGetChildren.setLong(2, parentId);
            List<RecordStructure> result = new ArrayList<>();
            try (ResultSet rs = psGetChildren.executeQuery()) {
                while (rs.next()) {
                    result.add(build(rs));
                }
            }

            return result;
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    public long getChildCount(long sessionId, long parentId) throws DatabaseException {
        try {
            psGetChildCount.setLong(1, sessionId);
            psGetChildCount.setLong(2, parentId);
            try (ResultSet rs = psGetChildCount.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    public void deleteLeaf(long sessionId, long structureId) throws DatabaseException {
        // TODO Implement

    }

    @Override
    public void deleteSubtree(long sessionId, long structureId) throws DatabaseException {
        // TODO Implement

    }

    private RecordStructure build(ResultSet rs) throws DatabaseException {
        try {
            RecordStructure rt = null;
            if (rs.next()) {
                rt =
                        new RecordStructure(
                                rs.getLong(SESSIONID),
                                rs.getLong(STRUCTUREID),
                                rs.getLong(PARENTID),
                                rs.getInt(HISTORYID),
                                rs.getString(NAME),
                                rs.getString(URL),
                                rs.getString(METHOD));
            }
            return rt;
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }
}

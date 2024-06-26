/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.program.database.mem;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import db.DBBuffer;
import db.DBHandle;
import ghidra.framework.data.OpenMode;
import ghidra.util.MonitoredInputStream;
import ghidra.util.exception.IOCancelledException;
import ghidra.util.exception.VersionException;
import ghidra.util.task.TaskMonitor;

/**
 * Database Adapter for storing and retrieving original file bytes.
 */
abstract class FileBytesAdapter {
	static final int MAX_BUF_SIZE = 1_000_000_000;

	public static final int FILENAME_COL = FileBytesAdapterV0.V0_FILENAME_COL;
	public static final int OFFSET_COL = FileBytesAdapterV0.V0_OFFSET_COL;
	public static final int SIZE_COL = FileBytesAdapterV0.V0_SIZE_COL;
	public static final int BUF_IDS_COL = FileBytesAdapterV0.V0_BUF_IDS_COL;
	public static final int LAYERED_BUF_IDS_COL = FileBytesAdapterV0.V0_LAYERED_BUF_IDS_COL;

	protected DBHandle handle;
	private static int maxBufSize = MAX_BUF_SIZE;	// shadowed so that it can be changed for testing

	protected MemoryMapDB memMap;

	FileBytesAdapter(DBHandle handle) {
		this.handle = handle;
	}

	static FileBytesAdapter getAdapter(DBHandle handle, OpenMode openMode, TaskMonitor monitor)
			throws VersionException, IOException {

		if (openMode == OpenMode.CREATE) {
			return new FileBytesAdapterV0(handle, true);
		}
		try {
			return new FileBytesAdapterV0(handle, false);
		}
		catch (VersionException e) {
			if (!e.isUpgradable() || openMode == OpenMode.UPDATE) {
				throw e;
			}
			FileBytesAdapter adapter = findReadOnlyAdapter(handle);
			if (openMode == OpenMode.UPGRADE) {
				adapter = upgrade(handle, adapter, monitor);
			}
			return adapter;
		}

	}

	private static FileBytesAdapter findReadOnlyAdapter(DBHandle handle) {
		return new FileBytesAdapterNoTable(handle);
	}

	private static FileBytesAdapter upgrade(DBHandle handle, FileBytesAdapter oldAdapter,
			TaskMonitor monitor) throws VersionException, IOException {
		return new FileBytesAdapterV0(handle, true);
	}

	/**
	 * Create {@link FileBytes} from specified input stream
	 * @param filename name of original file
	 * @param offset position of input stream within original file or 0 if no file
	 * @param size number of bytes to be read from input stream for stored file bytes
	 * @param is input stream
	 * @param monitor task monitor for progress and to allow cancellation.  This will be ignored if 
	 * input stream is a {@link MonitoredInputStream}.  Monitor may be reinitialized and progress
	 * updated while reading from input stream.
	 * @return new file bytes
	 * @throws IOException if error occurs reading input stream or writing file bytes to database
	 * @throws IOCancelledException if operation was cancelled
	 */
	abstract FileBytes createFileBytes(String filename, long offset, long size, InputStream is,
			TaskMonitor monitor) throws IOException;

	/**
	 * Returns a DBBuffer object for the given database buffer id
	 * @param bufferID the id of the first buffer in the DBBuffer.
	 * @return a DBBuffer object for the given database buffer id
	 * @throws IOException if a database IO error occurs.
	 */
	DBBuffer getBuffer(int bufferID) throws IOException {
		if (bufferID >= 0) {
			return handle.getBuffer(bufferID);
		}
		return null;
	}

	/**
	 * Returns a layered DBBuffer object for the given database buffer id
	 * @param bufferID the id of the first buffer in the DBBuffer.
	 * @param shadowBuffer the buffer to use for byte values unless the bytes have been
	 * explicitly set in this buffer.
	 * @return a DBBuffer object for the given database buffer id using the given shadow buffer.
	 * @throws IOException if a database IO error occurs.
	 */
	DBBuffer getBuffer(int bufferID, DBBuffer shadowBuffer) throws IOException {
		if (bufferID >= 0) {
			return handle.getBuffer(bufferID, shadowBuffer);
		}
		return null;
	}

	static int getMaxBufferSize() {
		return maxBufSize;
	}

	// *** FOR TESTING PURPOSES ONLY ***
	static void setMaxBufferSize(int testSize) {
		maxBufSize = testSize;
	}

	abstract List<FileBytes> getAllFileBytes();

	abstract void refresh() throws IOException;

	abstract boolean deleteFileBytes(FileBytes fileBytes) throws IOException;

}

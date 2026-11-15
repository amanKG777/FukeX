package com.boostofstudios.fukex.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.boostofstudios.fukex.BuildConfig
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface UpdateState {
	data object Idle : UpdateState
	// manual = the user asked, so the result (even "up to date") is shown
	data class Checking(val manual: Boolean) : UpdateState
	data class UpToDate(val version: String) : UpdateState
	data class Available(val release: ReleaseInfo) : UpdateState
	data class Downloading(val release: ReleaseInfo, val bytes: Long, val total: Long) : UpdateState
	// Downloaded and checked, but Android has not yet been told that FukeX may install apps
	data class NeedsPermission(val release: ReleaseInfo, val file: File) : UpdateState
	data class Failed(val message: String, val release: ReleaseInfo? = null) : UpdateState
}

class UpdateException(message: String) : Exception(message)

// Checks GitHub for a newer FukeX, downloads it, checks it really is FukeX signed by the same key,
// and hands it to Android's installer, which asks the user to confirm.
object UpdateManager {
	private const val TAG = "UpdateManager"
	private const val LATEST_URL = "https://api.github.com/repos/amankg777/fukex/releases/latest"
	private const val DIR = "updates"
	const val FILE_AUTHORITY_SUFFIX = ".updates"
	private const val MAX_APK_BYTES = 200L * 1024 * 1024

	private val client = OkHttpClient.Builder()
		.callTimeout(0, TimeUnit.SECONDS)
		.readTimeout(30, TimeUnit.SECONDS)
		.connectTimeout(15, TimeUnit.SECONDS)
		.build()
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private var job: Job? = null

	private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
	val state: StateFlow<UpdateState> = _state.asStateFlow()

	private fun dir(context: Context) = File(context.cacheDir, DIR)

	// Removes APKs left from an earlier update, which is where most of the cache would go
	fun cleanUp(context: Context) {
		try { dir(context).deleteRecursively() } catch (e: Exception) { Log.w(TAG, "Could not clean up", e) }
	}

	fun dismiss() {
		job?.cancel()
		_state.value = UpdateState.Idle
	}

	fun check(manual: Boolean) {
		if (job?.isActive == true) return
		_state.value = UpdateState.Checking(manual)
		job = scope.launch {
			_state.value = try {
				val release = withContext(Dispatchers.IO) { fetchLatest() }
				when {
					release == null -> UpdateState.UpToDate(BuildConfig.VERSION_NAME)
					SemanticVersion.isNewer(BuildConfig.VERSION_NAME, release.version) -> UpdateState.Available(release)
					else -> UpdateState.UpToDate(BuildConfig.VERSION_NAME)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.w(TAG, "Update check failed: $e", e)
				UpdateState.Failed(if (e is UpdateException) e.message ?: "Update check failed" else "Could not check for updates")
			}
			// A background check that finds nothing, or fails, should not interrupt anyone
			val result = _state.value
			if (!manual && (result is UpdateState.UpToDate || result is UpdateState.Failed)) _state.value = UpdateState.Idle
		}
	}

	fun download(context: Context) {
		val release = when (val s = _state.value) {
			is UpdateState.Available -> s.release
			is UpdateState.Failed -> s.release ?: return
			else -> return
		}
		if (job?.isActive == true) return
		val app = context.applicationContext
		_state.value = UpdateState.Downloading(release, 0, release.apkSizeBytes)
		job = scope.launch {
			try {
				val file = withContext(Dispatchers.IO) {
					val file = downloadApk(app, release) { done, total ->
						_state.value = UpdateState.Downloading(release, done, total)
					}
					verifyApk(app, file, release)
					file
				}
				_state.value = UpdateState.NeedsPermission(release, file)
				proceedToInstall(context)
			} catch (e: CancellationException) {
				cleanUp(app)
				throw e
			} catch (e: Exception) {
				Log.w(TAG, "Update download failed: $e", e)
				cleanUp(app)
				_state.value = UpdateState.Failed(if (e is UpdateException) e.message ?: "The update failed" else "The download failed", release)
			}
		}
	}

	// Installs straight away when Android allows it, otherwise stays on NeedsPermission for the dialog to explain
	private fun proceedToInstall(context: Context) {
		val s = _state.value as? UpdateState.NeedsPermission ?: return
		if (context.packageManager.canRequestPackageInstalls()) {
			install(context, s.file)
			_state.value = UpdateState.Idle
		}
		// Otherwise the dialog explains and offers to open Android's settings page, so it is not a surprise
	}

	// Called whenever the app comes back to the front, e.g. after the user flipped the switch in settings
	fun onResume(context: Context) {
		if (_state.value is UpdateState.NeedsPermission) proceedToInstall(context)
	}

	fun openInstallPermissionSettings(context: Context) {
		val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		try {
			context.startActivity(intent)
		} catch (e: Exception) {
			Log.w(TAG, "Could not open the install permission page", e)
			context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		}
	}

	private fun install(context: Context, file: File) {
		val uri = FileProvider.getUriForFile(context, context.packageName + FILE_AUTHORITY_SUFFIX, file)
		val intent = Intent(Intent.ACTION_VIEW)
			.setDataAndType(uri, "application/vnd.android.package-archive")
			.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
		context.startActivity(intent)
	}

	private fun fetchLatest(): ReleaseInfo? {
		val request = Request.Builder().url(LATEST_URL).header("Accept", "application/vnd.github+json").build()
		client.newCall(request).execute().use { response ->
			// No release published yet
			if (response.code == 404) return null
			if (!response.isSuccessful) throw UpdateException("GitHub answered ${response.code}")
			return ReleaseParser.parse(response.body?.string().orEmpty())
		}
	}

	private suspend fun downloadApk(context: Context, release: ReleaseInfo, onProgress: (Long, Long) -> Unit): File {
		val target = File(dir(context), "FukeX-${release.version}.apk")
		target.parentFile?.apply { deleteRecursively(); mkdirs() }
		try {
			client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
				if (!response.isSuccessful) throw UpdateException("The download failed (${response.code})")
				val body = response.body ?: throw UpdateException("The download was empty")
				val total = body.contentLength().takeIf { it > 0 } ?: release.apkSizeBytes
				if (total > MAX_APK_BYTES) throw UpdateException("The update is larger than expected")
				var done = 0L
				var lastReported = 0L
				body.byteStream().use { input ->
					target.outputStream().use { output ->
						val buffer = ByteArray(64 * 1024)
						while (true) {
							currentCoroutineContext().ensureActive()
							val n = input.read(buffer)
							if (n < 0) break
							output.write(buffer, 0, n)
							done += n
							if (done > MAX_APK_BYTES) throw UpdateException("The update is larger than expected")
							// Reporting every chunk would flood the UI; about 1% steps is plenty
							if (done - lastReported >= maxOf(total / 100, 256 * 1024)) {
								lastReported = done
								onProgress(done, total)
							}
						}
					}
				}
				onProgress(done, total)
			}
		} catch (e: IOException) {
			throw UpdateException("Could not reach GitHub. Check your internet connection.")
		}
		return target
	}

	// The checks that matter most: it must be FukeX, newer, intact, and signed with our own key.
	// Android would refuse a differently signed update anyway; this just says so before the installer does.
	private fun verifyApk(context: Context, file: File, release: ReleaseInfo) {
		if (release.apkSizeBytes > 0 && file.length() != release.apkSizeBytes) {
			throw UpdateException("The download is incomplete. Please try again.")
		}
		release.sha256?.let { expected ->
			val digest = MessageDigest.getInstance("SHA-256")
			file.inputStream().use { input ->
				val buffer = ByteArray(64 * 1024)
				while (true) {
					val n = input.read(buffer)
					if (n < 0) break
					digest.update(buffer, 0, n)
				}
			}
			if (digest.digest().joinToString("") { "%02x".format(it) } != expected) {
				throw UpdateException("The download did not match its checksum, so it was discarded")
			}
		}
		val pm = context.packageManager
		val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
		val archive = pm.getPackageArchiveInfo(file.path, flags) ?: throw UpdateException("The downloaded file is not a valid app")
		if (archive.packageName != context.packageName) throw UpdateException("The downloaded file is not FukeX")
		if (!SemanticVersion.isNewer(BuildConfig.VERSION_NAME, archive.versionName)) {
			throw UpdateException("The downloaded file is not newer than the version you have")
		}
		val installed = try {
			pm.getPackageInfo(context.packageName, flags)
		} catch (e: PackageManager.NameNotFoundException) {
			throw UpdateException("FukeX could not check its own signature")
		}
		val installedSigners = signers(installed)
		val newSigners = signers(archive)
		if (newSigners.isEmpty() || newSigners.none { it in installedSigners }) {
			throw UpdateException(
				"This update is signed with a different key than the FukeX on your phone, so Android would refuse it. " +
					"Uninstall FukeX and install the new version from the release page instead (this erases your playlists, so export them first)."
			)
		}
	}

	private fun signers(info: PackageInfo): Set<String> {
		@Suppress("DEPRECATION")
		val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
		return signatures.orEmpty().map { sig ->
			MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
		}.toSet()
	}
}

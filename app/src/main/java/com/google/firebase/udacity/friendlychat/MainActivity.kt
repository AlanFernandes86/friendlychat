package com.google.firebase.udacity.friendlychat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var mMessageListView: ListView
    private lateinit var mMessageAdapter: MessageAdapter
    private lateinit var mProgressBar: ProgressBar
    private lateinit var mPhotoPickerButton: ImageButton
    private lateinit var mMessageEditText: EditText
    private lateinit var mSendButton: Button
    private lateinit var mUsername: String
    private lateinit var mFirebaseDatabase: FirebaseDatabase
    private lateinit var mMessagesDatabaseReference: DatabaseReference
    private lateinit var mChildEventListener: ChildEventListener
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mAuthStateListener: FirebaseAuth.AuthStateListener
    private lateinit var mFirebaseStorage: FirebaseStorage
    private lateinit var mChatPhotoStorageReference: StorageReference

    private val signInLauncher = registerForActivityResult(
            FirebaseAuthUIActivityResultContract()
    ) { res ->
        this.onSignInResult(res)
    }

    private val getPhotoLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
    ) { res ->
        this.onGetPhotoResult(res)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mUsername = ANONYMOUS

        //Initialize Firebase components
        mFirebaseDatabase = Firebase.database
        mFirebaseAuth = Firebase.auth
        mFirebaseStorage = Firebase.storage
        mMessagesDatabaseReference = mFirebaseDatabase.reference.child("messages")
        mChatPhotoStorageReference = mFirebaseStorage.reference.child("chat_photos")

        // Initialize references to views
        mProgressBar = findViewById<View>(R.id.progressBar) as ProgressBar
        mMessageListView = findViewById<View>(R.id.messageListView) as ListView
        mPhotoPickerButton = findViewById<View>(R.id.photoPickerButton) as ImageButton
        mMessageEditText = findViewById<View>(R.id.messageEditText) as EditText
        mSendButton = findViewById<View>(R.id.sendButton) as Button

        // Initialize message ListView and its adapter
        val friendlyMessages: List<FriendlyMessage> = ArrayList()
        mMessageAdapter = MessageAdapter(this, R.layout.item_message, friendlyMessages)
        mMessageListView.adapter = mMessageAdapter

        // Initialize progress bar
        mProgressBar.visibility = ProgressBar.INVISIBLE

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/jpeg"
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            getPhotoLauncher.launch(intent)
        }

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                mSendButton.isEnabled = charSequence.toString().trim { it <= ' ' }.isNotEmpty()
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        mMessageEditText.filters = arrayOf<InputFilter>(LengthFilter(DEFAULT_MSG_LENGTH_LIMIT))

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener { // TODO: Send messages on click
            val friendlyMessage = FriendlyMessage(mMessageEditText.text.toString(), mUsername, null)
            mMessagesDatabaseReference.push().setValue(friendlyMessage)
            // Clear input box
            mMessageEditText.setText("")
        }

        mAuthStateListener = (FirebaseAuth.AuthStateListener {
            val firebaseUser = it.currentUser
            if (firebaseUser != null) {
                // User is signed in
                onSignedInInitialize(firebaseUser.displayName ?: ANONYMOUS)
            } else {
                // User is signed out
                onSignedOutCleanup()
                createSignInIntent()
            }
        })
    }

    private fun onSignedInInitialize(userName: String) {
        mUsername = userName
        attachDatabaseReadListener()
    }

    private fun attachDatabaseReadListener() {
        mChildEventListener = (object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val newFriendlyMessage = snapshot.getValue(FriendlyMessage::class.java)
                mMessageAdapter.add(newFriendlyMessage)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                //TODO("Not yet implemented")
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val friendlyMessage = snapshot.getValue(FriendlyMessage::class.java)
                mMessageAdapter.remove(friendlyMessage)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                //TODO("Not yet implemented")
            }

            override fun onCancelled(error: DatabaseError) {
                //TODO("Not yet implemented")
            }
        })
        mMessagesDatabaseReference.addChildEventListener(mChildEventListener)
    }

    private fun onSignedOutCleanup() {
        mUsername = ANONYMOUS
        mMessageAdapter.clear()
        detachDatabaseReadListener()
    }

    private fun detachDatabaseReadListener() {
        if (::mChildEventListener.isInitialized) {
            mMessagesDatabaseReference.removeEventListener(mChildEventListener)
        }
    }

    private fun createSignInIntent() {
        // Choose authentication providers
        val providers = arrayListOf(
                AuthUI.IdpConfig.EmailBuilder().build(),
                //AuthUI.IdpConfig.PhoneBuilder().build(),
                AuthUI.IdpConfig.GoogleBuilder().build(),
                //AuthUI.IdpConfig.FacebookBuilder().build(),
                //AuthUI.IdpConfig.TwitterBuilder().build()
        )

        // Create and launch sign-in intent
        val signInIntent = AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setIsSmartLockEnabled(false)
                .setAvailableProviders(providers)
                .build()
        signInLauncher.launch(signInIntent)
    }


    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        val response = result.idpResponse
        when (result.resultCode) {
            RESULT_OK -> {
                // Successfully signed in
                val user = FirebaseAuth.getInstance().currentUser
                Toast.makeText(this, "You're now signed in. Welcome to FriendlyChat.", Toast.LENGTH_SHORT).show()
                // ...
            }
            RESULT_CANCELED -> {
                finish()
            }
            else -> {
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                // ...
            }
        }
    }

    private fun onGetPhotoResult(result: ActivityResult) {
        when (result.resultCode) {
            RESULT_OK -> {
                val selectedPhotoUri = result.data?.data as Uri
                val photoRef = mChatPhotoStorageReference.child(selectedPhotoUri.lastPathSegment as String)
                val uploadTask = photoRef.putFile(selectedPhotoUri)

                uploadTask.continueWithTask { task ->
                    if (!task.isSuccessful) {
                        task.exception?.let {
                            throw it
                        }
                    }
                    photoRef.downloadUrl
                }.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val downloadUri = task.result as Uri
                        val friendlyMessage = FriendlyMessage(null, mUsername, downloadUri.toString())
                        mMessagesDatabaseReference.push().setValue(friendlyMessage)
                    }
                }
            }
            RESULT_CANCELED -> {
                Toast.makeText(this, "Cancel", Toast.LENGTH_SHORT).show()
            }
            else -> {
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sign_out_menu -> {
                AuthUI.getInstance().signOut(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        mFirebaseAuth.removeAuthStateListener(mAuthStateListener)
        detachDatabaseReadListener()
        mMessageAdapter.clear()
    }

    override fun onResume() {
        super.onResume()
        mFirebaseAuth.addAuthStateListener(mAuthStateListener)
    }

    companion object {
        const val TAG = "MainActivity"
        const val ANONYMOUS = "anonymous"
        const val DEFAULT_MSG_LENGTH_LIMIT = 1000
    }
}
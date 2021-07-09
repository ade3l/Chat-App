package com.example.chatapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;

    private ListView mMessageListView;//list view for showing the messages
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;

    public static final int RC_SIGN_IN=1;
    private FirebaseDatabase mFireDatabase;
    private DatabaseReference mMessegesDatabaseRefrence;
    private ChildEventListener mChildEventListener ;

    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        //Main access point to our database
        mFireDatabase=FirebaseDatabase.getInstance();

        //This is an object which references to a specific part of the database
        mMessegesDatabaseRefrence=mFireDatabase.getReference().child("messages");
//        mFireDatabase.getReference() returns a refrence to the root node of the db and .child gets us the messages portion of the database

        mFirebaseAuth=FirebaseAuth.getInstance();




        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        mSendButton.setOnClickListener(this);
        mPhotoPickerButton.setOnClickListener(this);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            //A textwatcher is used so that we cant press send for empty messages
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});


        //This listener will now get triggered for any changes in the children of the messages insteance

        mAuthStateListener= new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user=firebaseAuth.getCurrentUser();
                if(user!=null){
                    //user is signed in
                    onSignedInInitialise(user.getDisplayName());
                }else{
                    //user is signed out
                    //launch sign in flow
                    onSignedOutCleanUp();
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.GoogleBuilder().build(),
                                            new AuthUI.IdpConfig.EmailBuilder().build()))
                                    .build(),
                            RC_SIGN_IN);
                }

            }
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case (R.id.sendButton):
                FriendlyMessage friendlyMessage=new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
                //clear input box
                Log.i("mine",mMessegesDatabaseRefrence.toString());
                mMessegesDatabaseRefrence.push().setValue(friendlyMessage);
                mMessageEditText.setText("");
                break;
            case (R.id.photoPickerButton):
                //TODO: photo picker button code
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable  Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN){
            if(resultCode == RESULT_OK){
                Toast.makeText(this, "Signed In!", Toast.LENGTH_SHORT).show();
            } else if(resultCode == RESULT_CANCELED){
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        detachReadListener();
        mMessageAdapter.clear();
    }

    private void onSignedInInitialise(String username){
        mUsername=username;
        attachReadListener();

    }
    private void onSignedOutCleanUp(){
        mUsername=ANONYMOUS;
        mMessageAdapter.clear();
    }

    private void attachReadListener(){
        if(mChildEventListener==null) {
            mChildEventListener = new ChildEventListener() {
                //The DataSnapshot contains data from the Firebase database
                // at a specific location at the exact time the listener is triggered.
                @Override
                public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    //This is the method that gets called whenever a new message is inserted
                    //into the messages list
                    FriendlyMessage newMessage = snapshot.getValue(FriendlyMessage.class);
                    //getValue can take a parameter, which is a class
                    //By passing in this parameter the code will deserialize the message

                    mMessageAdapter.add(newMessage);
                }

                @Override
                public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
//                This gets called when the contents of an existing message gets changed.
                }

                @Override
                public void onChildRemoved(DataSnapshot snapshot) {
//                will get called when an existing message is deleted.
                }

                @Override
                public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
//                would get called if one of our messages changed position in the list
                }

                @Override
                public void onCancelled(DatabaseError error) {
//                indicates that some sort of error occurred when you are trying to make changes.
//                Typically, if this is being called it means that you don't have permission to
//                read the data
                }
            };

            mMessegesDatabaseRefrence.addChildEventListener(mChildEventListener);
        }
    }

    private void detachReadListener(){
        if(mChildEventListener!=null){
            mMessegesDatabaseRefrence.removeEventListener(mChildEventListener);
            mChildEventListener=null;
        }
    }
}
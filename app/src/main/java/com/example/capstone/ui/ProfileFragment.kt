package com.example.capstone.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.example.capstone.R
import com.example.capstone.databinding.FragmentProfileBinding
import com.example.capstone.utils.Constant.Companion.LOGIN_CREDENTIAL
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase


class ProfileFragment : Fragment() {

    private lateinit var fragmentProfileBinding: FragmentProfileBinding

    private lateinit var mGoogleSignInClient: GoogleSignInClient

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        fragmentProfileBinding = FragmentProfileBinding.inflate(inflater, container, false)
        return fragmentProfileBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        mGoogleSignInClient= GoogleSignIn.getClient(requireActivity(),gso)

        val sharedPreference = requireActivity().getSharedPreferences(LOGIN_CREDENTIAL, Context.MODE_PRIVATE)
        val emailData = sharedPreference.getString("email", "")
        val nameData = sharedPreference.getString("name", "")
        fragmentProfileBinding.tvEmail.text = emailData.toString()

        if (nameData?.isNotEmpty() == true) {
            fragmentProfileBinding.tvName.text = nameData.toString()
        }

        fragmentProfileBinding.tvAddFeedback.setOnClickListener {
            beginTransaction(AddFeedbackFragment())
        }
        fragmentProfileBinding.tvAboutUs.setOnClickListener {
            beginTransaction(AboutUsFragment())
        }

        fragmentProfileBinding.btnLogout.setOnClickListener {
            sharedPreference?.edit {
                this.clear()
            }
            Firebase.auth.signOut()
            mGoogleSignInClient.signOut().addOnCompleteListener {
                val intent= Intent(requireActivity(), LoginActivity::class.java)
                startActivity(intent)
                //finish()
            }
        }
    }

    private fun beginTransaction(fragment : Fragment){
        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        transaction.replace(R.id.container, fragment)
        transaction.addToBackStack("tag")
        transaction.commit()
    }
}
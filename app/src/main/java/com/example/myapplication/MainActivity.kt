package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cc.cicare.sdkcall.CiCareSdkCall
import cc.cicare.sdkcall.services.CiCareCallService
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TestServiceButtons(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(24.dp),
                        onStartOutbound = {
                            CiCareSdkCall.init(this).showIncoming(
                                "Annas",
                                "",
                                "djksfgakjsdghfjkadsfgajkdsfgjasd",
                                "http://sip-gq.c-icare.cc:8443/",
                                false
                            )
                        },
                        onStartInbound = {
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TestServiceButtons(
    modifier: Modifier = Modifier,
    onStartOutbound: () -> Unit,
    onStartInbound: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(onClick = onStartOutbound, modifier = Modifier.fillMaxWidth()) {
            Text("Start Outbound Call Service")
        }

        Button(onClick = onStartInbound, modifier = Modifier.fillMaxWidth()) {
            Text("Start Inbound Call Service")
        }
    }
}
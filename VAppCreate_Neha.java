package edu.sjsu.cmpe.cmpe283;

/*Import statements*/
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import com.vmware.vim25.HttpNfcLeaseDeviceUrl;
import com.vmware.vim25.HttpNfcLeaseInfo;
import com.vmware.vim25.HttpNfcLeaseState;
import com.vmware.vim25.OvfCreateDescriptorParams;
import com.vmware.vim25.OvfCreateDescriptorResult;
import com.vmware.vim25.OvfFile;
import com.vmware.vim25.ResourceAllocationInfo;
import com.vmware.vim25.ResourceConfigSpec;
import com.vmware.vim25.SharesInfo;
import com.vmware.vim25.SharesLevel;
import com.vmware.vim25.VAppConfigSpec;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HttpNfcLease;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualApp;
import com.vmware.vim25.mo.VirtualMachine;

/**
 * This class describes how two existing VMs are cloned and composed into a vApp.
 * It also describes how OVF file is generated.
 * 
 * Author: Neha Viswanathan, 010029097
 * Date: 04-21-2015
 */
public class VAppCreate_Neha {

	public static void main(String[] args) {
		String ip = args[0];
		String login = args[1];
		String password = args[2];
		ServiceInstance sInst = null;

		try {
			/*Instantiating ServiceInstance using the host IP and root id and password.*/ 
			sInst = new ServiceInstance(new URL(ip), login, password, true);
			Folder rootFolder = sInst.getRootFolder();
			System.out.println("vm folder = " + rootFolder.getName());

			/*Navigate to workspace folder.*/
			ResourcePool cmpePool = (ResourcePool) new InventoryNavigator(rootFolder).
					searchManagedEntity("ResourcePool", "CMPE283 SEC3");
			Folder nehaFolder = (Folder) sInst.getSearchIndex().
					findByInventoryPath("/CMPE LABS/vm/CMPE283 SEC3/workspace/Neha-097");
			//System.out.println(nehaFolder.getName());

			/*Specify the config specifications*/
			ResourceConfigSpec configSpec = new ResourceConfigSpec();

			/*Set SharesInfo*/
			SharesInfo shareInfo1 = new SharesInfo();
			shareInfo1.setLevel(SharesLevel.custom);
			shareInfo1.setShares(8000);

			/*Set ResourceAllocationInfo for memory*/
			ResourceAllocationInfo allocInfo1 = new ResourceAllocationInfo();
			allocInfo1.setReservation(2000L);
			allocInfo1.setLimit(10000L);
			allocInfo1.setExpandableReservation(true);
			allocInfo1.setShares(shareInfo1);
			configSpec.setMemoryAllocation(allocInfo1);

			/*Set ResourceAllocationInfo for CPU*/
			SharesInfo shareInfo2 = new SharesInfo();
			shareInfo2.setLevel(SharesLevel.custom);
			shareInfo2.setShares(8000);
			ResourceAllocationInfo allocInfo2 = new ResourceAllocationInfo();
			allocInfo2.setReservation(2000L);
			allocInfo2.setLimit(10000L);
			allocInfo2.setExpandableReservation(true);
			allocInfo2.setShares(shareInfo2);
			configSpec.setCpuAllocation(allocInfo2);

			/*Create an empty vApp*/
			VAppConfigSpec vAppSpec = new VAppConfigSpec();
			VirtualApp virtualApp = cmpePool.createVApp("neha-097-vAppNew", configSpec, vAppSpec, nehaFolder);
			//System.out.println("empty vApp created!");

			/*Clone the existing VMs*/
			String myVM1 = "Neha-ubuntu1404-097-1";
			String myVM2 = "Neha-ubuntu1404-097-2";
			ManagedEntity meGuest1 = new InventoryNavigator(rootFolder).searchManagedEntity("VirtualMachine", myVM1);
			VirtualMachine vm1 = (VirtualMachine) meGuest1;
			String cloneName1 = vm1.getName()+"-1";
			VirtualMachineCloneSpec vmCloneSpec1 = new VirtualMachineCloneSpec();
			vmCloneSpec1.setLocation(new VirtualMachineRelocateSpec());
			vmCloneSpec1.setPowerOn(false);
			vmCloneSpec1.setTemplate(false);
			Task cloneTask1 = vm1.cloneVM_Task((Folder)vm1.getParent(), cloneName1, vmCloneSpec1);
			cloneTask1.waitForTask();

			ManagedEntity meGuest2 = new InventoryNavigator(rootFolder).searchManagedEntity("VirtualMachine", myVM2);
			VirtualMachine vm = (VirtualMachine) meGuest2;
			String cloneName2 = vm.getName()+"-2";
			VirtualMachineCloneSpec vmCloneSpec2 = new VirtualMachineCloneSpec();
			vmCloneSpec2.setLocation(new VirtualMachineRelocateSpec());
			vmCloneSpec2.setPowerOn(false);
			vmCloneSpec2.setTemplate(false);
			Task cloneTask2 = vm.cloneVM_Task((Folder)vm.getParent(), cloneName2, vmCloneSpec2);
			cloneTask2.waitForTask();

			/*Move the cloned VMs into the empty vApp*/
			ManagedEntity mEnt1 = new InventoryNavigator(rootFolder).searchManagedEntity("VirtualMachine", cloneName1);
			ManagedEntity mEnt2 = new InventoryNavigator(rootFolder).searchManagedEntity("VirtualMachine", cloneName2);
			ManagedEntity[] mEntArr = {mEnt1, mEnt2};
			virtualApp.moveIntoResourcePool(mEntArr);
			System.out.println("Move vms to vapp is Done!");

			/*Export the OVF file*/
			HttpNfcLease httpLease = virtualApp.exportVApp();

			HttpNfcLeaseState leaseState;

			/*Until the vApp is exported with status as error or ready, an infinite loop runs*/
			while(true) {
				leaseState = httpLease.getState();
				if(httpLease.equals(leaseState.error) || httpLease.equals(leaseState.ready)) {
					break;
				}
			}

			/*Export the vmdk file*/
			if(httpLease.equals(leaseState.ready)) {
				HttpNfcLeaseInfo leaseInfo = httpLease.getInfo();
				leaseInfo.setLeaseTimeout(3600000);
				HttpNfcLeaseDeviceUrl[] deviceUrlArr = leaseInfo.getDeviceUrl();
				int count=0;
				OvfFile[] ovfFileArr = new OvfFile[deviceUrlArr.length];
				for(HttpNfcLeaseDeviceUrl device : deviceUrlArr) {
					String deviceKey = device.getKey();
					String deviceURL = device.getUrl();
					String fileName = virtualApp.getName() + "-" + count +".vmdk";
					String cookie = sInst.getServerConnection().getVimService().getWsc().getCookie();

					HostnameVerifier verify = new HostnameVerifier() {

						@Override
						public boolean verify(String hostname, SSLSession session) {
							// TODO Auto-generated method stub
							return true;
						}
					};

					HttpsURLConnection.setDefaultHostnameVerifier(verify);
					URL url = new URL(deviceURL);
					HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

					connection.setDoInput(true);
					connection.setDoOutput(true);
					connection.setAllowUserInteraction(true);
					connection.setRequestProperty("Cookie", cookie);
					connection.connect();

					InputStream input = connection.getInputStream();
					FileOutputStream output = new FileOutputStream(
							new File("C:\\Spring2015\\283\\CMPE283_HW3_VMS_NehaViswanathan_097\\ExtraCredit", fileName));
					byte[] byteVal = new byte[1000000];

					int byteTransfer = 0;
					int length = input.read(byteVal);
					while (length > 0) {
						output.write(byteVal, 0, length);
						byteTransfer = byteTransfer + length;
					}

					input.close();
					output.close();

					OvfFile ovfFile = new OvfFile();
					ovfFile.setPath(fileName);
					ovfFile.setDeviceId(deviceKey);
					ovfFile.setSize(byteTransfer);
					ovfFileArr[count] = ovfFile;
					count++;
				}

				/*Create vApp OVF File from vmdk file*/
				OvfCreateDescriptorParams ovfDesc = new OvfCreateDescriptorParams();
				ovfDesc.setOvfFiles(ovfFileArr);
				OvfCreateDescriptorResult ovfDescRes = sInst.getOvfManager().createDescriptor(virtualApp, ovfDesc);

				String ovfpath = virtualApp.getName()+ ".ovf";
				FileWriter fileW = new FileWriter(
						new File("C:\\Spring2015\\283\\CMPE283_HW3_VMS_NehaViswanathan_097\\ExtraCredit", ovfpath));
				fileW.write(ovfDescRes.getOvfDescriptor());
				fileW.close();
				System.out.println("OVF file is generated!");
			}
		}
		catch(Exception e) {
			System.err.println("Exception occurred :: " + e.getMessage());
			e.printStackTrace();
		}
		finally{

		}
	}

}
